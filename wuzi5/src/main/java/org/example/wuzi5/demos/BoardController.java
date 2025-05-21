
package org.example.wuzi5.demos;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.wuzi5.demos.entity.GameRecord;
import org.example.wuzi5.demos.entity.User;
import org.example.wuzi5.demos.mapper.GameRecordMapper;
import org.example.wuzi5.demos.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.sql.Timestamp;

@Controller
public class BoardController {
    private List<List<String>> board = new ArrayList<>();
    private int player = 1;
    private boolean aiMode = false;
    private boolean gameOver = false;

    private static final Map<String, Map<String, Object>> activeGames = new HashMap<>();
    private static final Set<String> activeUsers = new HashSet<>();

    @Autowired
    private GameRecordMapper gameRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/board")
    public String getBoard(Model model, Authentication authentication) {
        int boardSize = 15;
        board = new ArrayList<>();
        for (int i = 0; i < boardSize; i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < boardSize; j++) {
                row.add("0");
            }
            board.add(row);
        }
        player = 1;
        model.addAttribute("board", board);
        if (authentication != null && authentication.isAuthenticated()) {
            model.addAttribute("username", authentication.getName());
            String gameId = UUID.randomUUID().toString();
            Map<String, Object> gameState = new HashMap<>();
            gameState.put("board", new ArrayList<>(board));
            gameState.put("player", player);
            gameState.put("aiMode", aiMode);
            gameState.put("gameOver", gameOver);
            gameState.put("username", authentication.getName());
            activeGames.put(gameId, gameState);
            activeUsers.add(authentication.getName());
            model.addAttribute("gameId", gameId);

            User user = userMapper.findUserByUsername(authentication.getName());
            boolean isAdmin = (user != null && user.getRole().equals("ADMIN"));
            model.addAttribute("isAdmin", isAdmin);
        }
        return "index";
    }

    @PostMapping("/makeMove")
    public ResponseEntity<Map<String, Object>> makeMove(@RequestBody Map<String, Object> request) {
        if (gameOver) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "游戏已结束，无法继续落子");
            return ResponseEntity.ok(response);
        }
        int row = (Integer) request.get("row");
        int col = (Integer) request.get("col");
        String gameId = (String) request.get("gameId");

        if (board.isEmpty()) {
            int boardSize = 15;
            for (int i = 0; i < boardSize; i++) {
                List<String> rowList = new ArrayList<>();
                for (int j = 0; j < boardSize; j++) {
                    rowList.add("0");
                }
                board.add(rowList);
            }
        }

        String piece = player == 1 ? "X" : "O";
        board.get(row).set(col, piece);
        boolean success = true;
        boolean win = checkWin(board, row, col, player);
        if (win) gameOver = true;

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("player", player);
        response.put("piece", piece);
        response.put("win", win);

        player = player == 1 ? 2 : 1;

        if (aiMode && player == 2) {
            makeAIMove();
            int aiRow = getLastAIMoveRow();
            int aiCol = getLastAIMoveCol();
            boolean aiWin = checkWin(board, aiRow, aiCol, 2);
            if (aiWin) gameOver = true;
            response.put("player", 1);
            response.put("aiRow", aiRow);
            response.put("aiCol", aiCol);
            response.put("aiWin", aiWin);
        }

        Map<String, Object> gameState = activeGames.get(gameId);
        if (gameState != null) {
            gameState.put("board", new ArrayList<>(board));
            gameState.put("player", player);
            gameState.put("gameOver", gameOver);
            gameState.put("aiMode", aiMode);
            messagingTemplate.convertAndSend("/topic/game/" + gameId, gameState);
            System.out.println("Broadcasting game state for gameId: " + gameId);
        }

        return ResponseEntity.ok(response);
    }

    private int lastAIMoveRow;
    private int lastAIMoveCol;

    private void makeAIMove() {
        int[] bestMove = findBestMove();
        int row = bestMove[0];
        int col = bestMove[1];
        board.get(row).set(col, "O");
        lastAIMoveRow = row;
        lastAIMoveCol = col;
        player = player == 1 ? 2 : 1;
    }

    private int[] findBestMove() {
        int boardSize = board.size();
        int[] bestMove = new int[]{-1, -1};
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        int depth = board.stream().flatMap(List::stream).filter(cell -> !cell.equals("0")).count() < 10 ? 1 : 2; // Reduce depth early game

        // Check for immediate winning move
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                if (board.get(i).get(j).equals("0")) {
                    board.get(i).set(j, "O");
                    if (checkWin(board, i, j, 2)) {
                        board.get(i).set(j, "0");
                        return new int[]{i, j};
                    }
                    board.get(i).set(j, "0");
                }
            }
        }

        // Check for immediate blocking of opponent's win
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                if (board.get(i).get(j).equals("0")) {
                    board.get(i).set(j, "X");
                    if (checkWin(board, i, j, 1)) {
                        board.get(i).set(j, "0");
                        return new int[]{i, j};
                    }
                    board.get(i).set(j, "0");
                }
            }
        }

        // Evaluate moves near existing pieces
        Set<int[]> candidateMoves = getCandidateMoves();
        for (int[] move : candidateMoves) {
            int i = move[0];
            int j = move[1];
            board.get(i).set(j, "O");
            int score = minimax(board, depth - 1, false, alpha, beta);
            board.get(i).set(j, "0");
            if (score > bestScore) {
                bestScore = score;
                bestMove[0] = i;
                bestMove[1] = j;
            }
            alpha = Math.max(alpha, bestScore);
            if (beta <= alpha) break; // Alpha-beta pruning
        }

        // Fallback to center if no moves evaluated
        if (bestMove[0] == -1) {
            bestMove[0] = boardSize / 2;
            bestMove[1] = boardSize / 2;
        }
        return bestMove;
    }

    private Set<int[]> getCandidateMoves() {
        Set<int[]> candidates = new HashSet<>();
        int boardSize = board.size();
        int radius = 2; // Search within 2 cells of existing pieces

        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                if (!board.get(i).get(j).equals("0")) {
                    for (int di = -radius; di <= radius; di++) {
                        for (int dj = -radius; dj <= radius; dj++) {
                            int ni = i + di;
                            int nj = j + dj;
                            if (ni >= 0 && ni < boardSize && nj >= 0 && nj < boardSize && board.get(ni).get(nj).equals("0")) {
                                candidates.add(new int[]{ni, nj});
                            }
                        }
                    }
                }
            }
        }
        return candidates;
    }

    private int minimax(List<List<String>> board, int depth, boolean isMaximizing, int alpha, int beta) {
        if (depth == 0 || gameOver) {
            return evaluateBoard();
        }

        int boardSize = board.size();
        Set<int[]> candidateMoves = getCandidateMoves();

        if (isMaximizing) {
            int maxEval = Integer.MIN_VALUE;
            for (int[] move : candidateMoves) {
                int i = move[0];
                int j = move[1];
                board.get(i).set(j, "O");
                if (checkWin(board, i, j, 2)) {
                    board.get(i).set(j, "0");
                    return 1000000; // Immediate win
                }
                int eval = minimax(board, depth - 1, false, alpha, beta);
                board.get(i).set(j, "0");
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break;
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (int[] move : candidateMoves) {
                int i = move[0];
                int j = move[1];
                board.get(i).set(j, "X");
                if (checkWin(board, i, j, 1)) {
                    board.get(i).set(j, "0");
                    return -1000000; // Opponent's win
                }
                int eval = minimax(board, depth - 1, true, alpha, beta);
                board.get(i).set(j, "0");
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break;
            }
            return minEval;
        }
    }

    private int evaluateBoard() {
        int score = 0;
        int boardSize = board.size();
        int filledCount = (int) board.stream().flatMap(List::stream).filter(cell -> !cell.equals("0")).count();
        int totalCells = boardSize * boardSize;
        float fillRate = (float) filledCount / totalCells;
        int attackWeight = (int) (10 + 15 * (1 - fillRate));
        int defenseWeight = (int) (15 + 20 * fillRate);

        // Evaluate only candidate moves
        Set<int[]> candidateMoves = getCandidateMoves();
        for (int[] move : candidateMoves) {
            int i = move[0];
            int j = move[1];
            int attackScore = evaluatePosition(i, j, "O");
            int threatScore = evaluatePosition(i, j, "X");
            score += attackScore * attackWeight - threatScore * defenseWeight;
        }

        // Center preference
        int centerX = boardSize / 2;
        int centerY = boardSize / 2;
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                if (board.get(i).get(j).equals("O")) {
                    int distanceToCenter = Math.abs(i - centerX) + Math.abs(j - centerY);
                    score += (boardSize - distanceToCenter) * 50;
                }
            }
        }
        return score;
    }

    private int evaluatePosition(int row, int col, String piece) {
        int score = 0;
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        board.get(row).set(col, piece);

        for (int[] dir : directions) {
            int dRow = dir[0];
            int dCol = dir[1];
            int count = 1;
            int emptyFront = 0, emptyBack = 0;
            boolean blockedFront = false, blockedBack = false;

            // Forward direction
            for (int i = 1; i <= 4; i++) {
                int r = row + i * dRow;
                int c = col + i * dCol;
                if (r < 0 || r >= board.size() || c < 0 || c >= board.size()) {
                    blockedFront = true;
                    break;
                }
                String cell = board.get(r).get(c);
                if (cell.equals(piece)) count++;
                else if (cell.equals("0")) emptyFront++;
                else {
                    blockedFront = true;
                    break;
                }
            }

            // Backward direction
            for (int i = 1; i <= 4; i++) {
                int r = row - i * dRow;
                int c = col - i * dCol;
                if (r < 0 || r >= board.size() || c < 0 || c >= board.size()) {
                    blockedBack = true;
                    break;
                }
                String cell = board.get(r).get(c);
                if (cell.equals(piece)) count++;
                else if (cell.equals("0")) emptyBack++;
                else {
                    blockedBack = true;
                    break;
                }
            }

            boolean isLive = !blockedFront && !blockedBack;
            int totalEmpty = emptyFront + emptyBack;
            boolean hasSingleBlock = blockedFront ^ blockedBack;

            if (count >= 5) score += 1000000; // Winning line
            else if (count == 4) {
                if (isLive && totalEmpty >= 2) score += 500000; // Open four
                else if (isLive && totalEmpty == 1) score += 200000; // Half-open four
                else if (hasSingleBlock && totalEmpty >= 1) score += 100000; // Blocked four
                else score += 50000; // Other four
            } else if (count == 3) {
                if (isLive && totalEmpty >= 2) score += 100000; // Open three
                else if (isLive && totalEmpty == 1) score += 50000; // Half-open three
                else if (hasSingleBlock && totalEmpty >= 1) score += 20000; // Blocked three
                else score += 10000; // Other three
            } else if (count == 2) {
                if (isLive && totalEmpty >= 2) score += 5000; // Open two
                else if (isLive && totalEmpty == 1) score += 2000; // Half-open two
                else if (hasSingleBlock && totalEmpty >= 1) score += 1000; // Blocked two
                else score += 500; // Other two
            } else if (count == 1) {
                int centerX = board.size() / 2;
                int centerY = board.size() / 2;
                int distanceToCenter = Math.abs(row - centerX) + Math.abs(col - centerY);
                score += (board.size() - distanceToCenter) * 10;
            }
        }
        board.get(row).set(col, "0");
        return score;
    }

    private int getLastAIMoveRow() {
        return lastAIMoveRow;
    }

    private int getLastAIMoveCol() {
        return lastAIMoveCol;
    }

    private boolean checkWin(List<List<String>> board, int row, int col, int player) {
        String piece = player == 1 ? "X" : "O";
        int count;

        count = 0;
        for (int c = Math.max(0, col - 4); c <= Math.min(board.size() - 1, col + 4); c++) {
            if (board.get(row).get(c).equals(piece)) count++;
            else count = 0;
            if (count == 5) return true;
        }

        count = 0;
        for (int r = Math.max(0, row - 4); r <= Math.min(board.size() - 1, row + 4); r++) {
            if (board.get(r).get(col).equals(piece)) count++;
            else count = 0;
            if (count == 5) return true;
        }

        count = 0;
        for (int i = -4; i <= 4; i++) {
            int r = row + i;
            int c = col + i;
            if (r >= 0 && r < board.size() && c >= 0 && c < board.size() && board.get(r).get(c).equals(piece)) count++;
            else count = 0;
            if (count == 5) return true;
        }

        count = 0;
        for (int i = -4; i <= 4; i++) {
            int r = row + i;
            int c = col - i;
            if (r >= 0 && r < board.size() && c >= 0 && c < board.size() && board.get(r).get(c).equals(piece)) count++;
            else count = 0;
            if (count == 5) return true;
        }
        return false;
    }

    @PostMapping("/resetGame")
    public ResponseEntity<Map<String, Object>> resetGame() {
        board = new ArrayList<>();
        int boardSize = 15;
        for (int i = 0; i < boardSize; i++) {
            List<String> row = new ArrayList<>();
            for (int j = 0; j < boardSize; j++) {
                row.add("0");
            }
            board.add(row);
        }
        player = 1;
        gameOver = false;
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/toggleAIMode")
    public ResponseEntity<Map<String, Object>> toggleAIMode() {
        aiMode = !aiMode;
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("aiMode", aiMode);
        if (aiMode && player == 2) {
            makeAIMove();
            int aiRow = getLastAIMoveRow();
            int aiCol = getLastAIMoveCol();
            boolean aiWin = checkWin(board, aiRow, aiCol, 2);
            if (aiWin) gameOver = true;
            response.put("player", 1);
            response.put("aiRow", aiRow);
            response.put("aiCol", aiCol);
            response.put("aiWin", aiWin);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/saveGame")
    public ResponseEntity<Map<String, Object>> saveGame(@RequestBody Map<String, String> request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(401).body(response);
        }
        String username = authentication.getName();
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.ok(response);
        }

        StringBuilder boardState = new StringBuilder();
        for (List<String> row : board) {
            for (String cell : row) {
                boardState.append(cell == null ? "0" : cell.equals(" ") ? "0" : cell);
            }
            boardState.append(",");
        }
        boardState.deleteCharAt(boardState.length() - 1);

        String moveHistory = request.get("moveHistory") != null ? request.get("moveHistory") : "";

        GameRecord gameRecord = new GameRecord();
        gameRecord.setUserId(userId);
        gameRecord.setBoardState(boardState.toString());
        gameRecord.setCurrentPlayer(player);
        gameRecord.setGameOver(gameOver);
        gameRecord.setAiMode(aiMode);
        gameRecord.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        gameRecordMapper.insert(gameRecord);

        rabbitTemplate.convertAndSend("game-notifications", "Game saved for user: " + username + ", recordId: " + gameRecord.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("recordId", gameRecord.getId());
        return ResponseEntity.ok(response);
    }

    @Cacheable(value = "gameRecords", key = "#recordId + '-' + #authentication.name")
    @PostMapping("/loadGame")
    public ResponseEntity<Map<String, Object>> loadGame(@RequestBody Map<String, String> request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(401).body(response);
        }
        String username = authentication.getName();
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.ok(response);
        }

        String recordId = request.get("recordId");
        GameRecord gameRecord = gameRecordMapper.selectById(recordId);
        if (gameRecord == null || !gameRecord.getUserId().equals(userId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "游戏记录不存在或无权限");
            return ResponseEntity.ok(response);
        }

        String boardState = gameRecord.getBoardState();
        if (boardState == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "游戏记录无效，棋盘状态为空");
            return ResponseEntity.ok(response);
        }

        String[] rows = boardState.split(",");
        board = new ArrayList<>();
        for (String row : rows) {
            List<String> rowList = new ArrayList<>();
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                rowList.add(c == '0' ? "0" : c == 'X' ? "X" : "O");
            }
            board.add(rowList);
        }

        player = gameRecord.getCurrentPlayer();
        gameOver = gameRecord.isGameOver();
        aiMode = gameRecord.isAiMode();

        rabbitTemplate.convertAndSend("game-notifications", "Game loaded for user: " + username + ", recordId: " + recordId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("board", board);
        response.put("player", player);
        response.put("gameOver", gameOver);
        response.put("aiMode", aiMode);
        response.put("recordId", recordId);
        return ResponseEntity.ok(response);
    }

    @Cacheable(value = "gameList", key = "#authentication.name")
    @GetMapping("/listGames")
    public ResponseEntity<List<Map<String, Object>>> listGames(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Collections.emptyList());
        }
        String username = authentication.getName();
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<GameRecord> gameRecords = gameRecordMapper.selectList(
                new QueryWrapper<GameRecord>().eq("user_id", userId).orderByDesc("created_at")
        );

        List<Map<String, Object>> response = new ArrayList<>();
        for (GameRecord record : gameRecords) {
            Map<String, Object> gameData = new HashMap<>();
            gameData.put("id", record.getId());
            gameData.put("createdAt", record.getCreatedAt().toString());
            gameData.put("aiMode", record.isAiMode());
            gameData.put("gameOver", record.isGameOver());
            gameData.put("currentPlayer", record.getCurrentPlayer());
            response.add(gameData);
        }
        return ResponseEntity.ok(response);
    }

    @CacheEvict(value = "gameList", key = "#authentication.name")
    @PostMapping("/deleteGame")
    public ResponseEntity<Map<String, Object>> deleteGame(@RequestBody Map<String, String> request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(401).body(response);
        }
        String username = authentication.getName();String username = authentication.getName()；
        Long userId = getUserIdByUsername(username);
        if (userId == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.ok(response);
        }

        String recordId = request.get("recordId");
        GameRecord gameRecord = gameRecordMapper.selectById(recordId);
        if (gameRecord == null || !gameRecord.getUserId().equals(userId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "游戏记录不存在或无权限");
            return ResponseEntity.ok(response);
        }

        gameRecordMapper.deleteById(recordId);

        rabbitTemplate.convertAndSend("game-notifications", "Game deleted for user: " + username + ", recordId: " + recordId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public   公共 void   无效 cleanOldGameRecords() {
        Timestamp   时间戳 oneMonthAgo = new   新 Timestamp   时间戳(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
        gameRecordMapper.delete   删除(new   新 QueryWrapper<GameRecord>().lt("created_at", oneMonthAgo));
        rabbitTemplate.convertAndSend("game-notifications", "Cleaned old game records before: " + oneMonthAgo);
    }

    private   私人 Long   长 getUserIdByUsername(String   字符串 username   用户名) {
        try   试一试 {
            Long   长 userId   用户标识 = userMapper.findUserIdByUsername(username);
            if   如果 (userId == null   零) System.out   出.println("User not found in users table: " + username);
            return   返回 userId;
        } catch   抓 (Exception e) {
            System.out.println("Error fetching user_id for username: " + username);
            return   返回 null   零;
        }
    }

    @GetMapping("/activeGames")
    public   公共 ResponseEntity<Map   地图<String   字符串, Object   对象>> listActiveGames(Authentication   身份验证 authentication   身份验证) {
        Map   地图<String   字符串, Object   对象> response   响应 = new   新 HashMap<>();
        if   如果 (authentication == null   零 || !authentication.isAuthenticated()) {
            response.put("success"   “成功”, false   假);
            response.put("message"   “消息”, "请先登录");
            return   返回 ResponseEntity.status   状态(401).body   身体(response);
        }
        String   字符串 currentUsername = authentication.getName();
        List   列表<Map   地图<String   字符串, Object   对象>> games   游戏 = new   新 ArrayList<>();
        Set   集<String   字符串> uniqueUsers = new   新 HashSet<>();
        for   为 (Map   地图.Entry<String   字符串, Map   地图<String   字符串, Object   对象>> entry   条目 : activeGames.entrySet()) {
            String   字符串 username   用户名 = (String   字符串) entry.getValue().get   得到("username"   “用户名”);
            if   如果 (username != null   零 && activeUsers.contains   包含(username) && !username.equals   =(currentUsername) && uniqueUsers.add   添加(username)) {
                Map   地图<String   字符串, Object   对象> gameInfo = new   新 HashMap<>();
                gameInfo.put   把("gameId"   “gameId”, entry.getKey());
                gameInfo.put   把("username"   “用户名”, username);
                games.add   添加(gameInfo);
            }
        }
        if   如果 (games.isEmpty()) {
            response.put   把("success"   “成功”, false   假);
            response.put   把("message"   “消息”, "当前没有其他用户可供观战");
            return   返回 ResponseEntity.ok(response);
        }
        response.put   把("success"   “成功”, true   真正的);
        response.put   把("games"   “游戏”, games);
        return   返回 ResponseEntity.ok(response);
    }

    @PostMapping("/logoutCleanup")
    public   公共 ResponseEntity<Map   地图<String   字符串, Object   对象>> logoutCleanup(@RequestBody Map   地图<String   字符串, String   字符串> request, Authentication   身份验证 authentication   身份验证) {
        if   如果 (authentication != null   零 && authentication.isAuthenticated()) {
            String   字符串 username   用户名 = authentication.getName();
            activeUsers.remove   删除(username);
            activeGames.entrySet().removeIf(entry   条目 -> entry.getValue().get   得到("username"   “用户名”).equals(username));
        }
        Map   地图<String   字符串, Object   对象> response   响应 = new   新 HashMap<>();
        response.put("success"   “成功”, true   真正的);
        return   返回 ResponseEntity.ok(response);
    }
}
