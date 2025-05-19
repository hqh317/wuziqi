package org.example.wuzi5.demos;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.wuzi5.demos.entity.GameRecord;
import org.example.wuzi5.demos.mapper.GameRecordMapper;
import org.example.wuzi5.demos.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    private GameRecordMapper gameRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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
        }
        return "index";
    }

    @PostMapping("/makeMove")
    public ResponseEntity<Map<String, Object>> makeMove(@RequestBody Map<String, Integer> request) {
        if (gameOver) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "游戏已结束，无法继续落子");
            return ResponseEntity.ok(response);
        }
        int row = request.get("row");
        int col = request.get("col");

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
        if (win) {
            gameOver = true;
        }

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
            if (aiWin) {
                gameOver = true;
            }
            response.put("player", 1);
            response.put("aiRow", aiRow);
            response.put("aiCol", aiCol);
            response.put("aiWin", aiWin);
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
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = new int[]{-1, -1};
        for (int i = 0; i < boardSize; i++) {
            for (int j = 0; j < boardSize; j++) {
                if (board.get(i).get(j).equals("0")) {
                    board.get(i).set(j, "O");
                    int score = evaluatePosition(i, j);
                    board.get(i).set(j, "0");
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove[0] = i;
                        bestMove[1] = j;
                    }
                }
            }
        }
        return bestMove;
    }

    private int evaluatePosition(int row, int col) {
        int score = 0;
        int filledCount = (int)board.stream().flatMap(List::stream).filter(cell -> !cell.equals("0")).count();
        int totalCells = board.size() * board.size();
        float fillRate = (float)filledCount / totalCells;
        int defenseWeight = (int)(8 + 12 * fillRate);
        int attackWeight = (int)(3 + 7 * (1 - fillRate));

        board.get(row).set(col, "O");
        int attackScore = evaluateDirection(row, col, 0, 1) * attackWeight +
                evaluateDirection(row, col, 1, 0) * attackWeight +
                evaluateDirection(row, col, 1, 1) * attackWeight +
                evaluateDirection(row, col, 1, -1) * attackWeight;
        board.get(row).set(col, "0");

        board.get(row).set(col, "X");
        int threatScore = evaluateDirection(row, col, 0, 1) * defenseWeight +
                evaluateDirection(row, col, 1, 0) * defenseWeight +
                evaluateDirection(row, col, 1, 1) * defenseWeight +
                evaluateDirection(row, col, 1, -1) * defenseWeight;
        board.get(row).set(col, "0");

        if (threatScore >= 180000) {
            score += threatScore * 2.5;
        } else if (threatScore >= 150000) {
            score += threatScore * 2.2;
        } else if (threatScore >= 120000) {
            score += threatScore * 1.8;
        } else if (threatScore >= 80000) {
            score += threatScore * 1.5;
        } else if (threatScore >= 20000) {
            score += threatScore * 1.2;
        } else {
            score += attackScore;
            int centerX = board.size() / 2;
            int centerY = board.size() / 2;
            int distanceToCenter = Math.abs(row - centerX) + Math.abs(col - centerY);
            score += (board.size() - distanceToCenter) * 20;
            if (evaluateDirection(row, col, 0, 1) >= 8000 || evaluateDirection(row, col, 1, 0) >= 8000) {
                score += 5000;
            }
        }

        if (attackScore > 120000 && threatScore < 150000) {
            int multiDirAttack = (evaluateDirection(row, col, 0, 1) + evaluateDirection(row, col, 1, 0)) / 2;
            score += multiDirAttack * (2.0f + 0.5f * fillRate);
        }
        return score;
    }

    private int evaluateDirection(int row, int col, int dRow, int dCol) {
        String piece = board.get(row).get(col);
        int count = 1;
        int emptyFront = 0, emptyBack = 0;
        boolean blockedFront = false, blockedBack = false;

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

        if (count >= 5) return 200000;
        if (count == 4) {
            if (isLive && totalEmpty >= 2) return 180000;
            if (isLive && totalEmpty == 1) return 160000;
            if (hasSingleBlock && totalEmpty >= 1) return 150000;
            return 120000;
        }
        if (count == 3) {
            if (isLive && totalEmpty >= 2) return 120000;
            if (isLive && totalEmpty == 1) return 100000;
            if (hasSingleBlock && totalEmpty >= 1) return 80000;
            return 50000;
        }
        if (count == 2) {
            if (isLive && totalEmpty >= 2) return 20000;
            if (isLive && totalEmpty == 1) return 15000;
            if (hasSingleBlock && totalEmpty >= 1) return 10000;
            return 8000;
        }
        if (count == 1) {
            int centerX = board.size() / 2;
            int centerY = board.size() / 2;
            int distanceToCenter = Math.abs(row - centerX) + Math.abs(col - centerY);
            return (board.size() - distanceToCenter) * 5;
        }
        return 0;
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
            if (board.get(row).get(c).equals(piece)) {
                count++;
                if (count == 5) return true;
            } else {
                count = 0;
            }
        }

        count = 0;
        for (int r = Math.max(0, row - 4); r <= Math.min(board.size() - 1, row + 4); r++) {
            if (board.get(r).get(col).equals(piece)) {
                count++;
                if (count == 5) return true;
            } else {
                count = 0;
            }
        }

        count = 0;
        for (int i = -4; i <= 4; i++) {
            int r = row + i;
            int c = col + i;
            if (r >= 0 && r < board.size() && c >= 0 && c < board.size() && board.get(r).get(c).equals(piece)) {
                count++;
                if (count == 5) return true;
            } else {
                count = 0;
            }
        }

        count = 0;
        for (int i = -4; i <= 4; i++) {
            int r = row + i;
            int c = col - i;
            if (r >= 0 && r < board.size() && c >= 0 && c < board.size() && board.get(r).get(c).equals(piece)) {
                count++;
                if (count == 5) return true;
            } else {
                count = 0;
            }
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
            if (aiWin) {
                gameOver = true;
            }
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

        String moveHistory = request.get("moveHistory");
        if (moveHistory == null) {
            moveHistory = "";
        }

        System.out.println("Saving game: user_id=" + userId + ", boardState=" + boardState + ", player=" + player + ", gameOver=" + gameOver + ", aiMode=" + aiMode + ", moveHistory=" + moveHistory);

        GameRecord gameRecord = new GameRecord();
        gameRecord.setUserId(userId);
        gameRecord.setBoardState(boardState.toString());
        gameRecord.setCurrentPlayer(player);
        gameRecord.setGameOver(gameOver);
        gameRecord.setAiMode(aiMode);
        Timestamp createdAt = new Timestamp(System.currentTimeMillis());
        System.out.println("Setting createdAt to: " + createdAt); // Debug log
        gameRecord.setCreatedAt(createdAt);

        gameRecordMapper.insert(gameRecord);
        System.out.println("Game saved successfully, recordId=" + gameRecord.getId());

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

        System.out.println("Loading game: user_id=" + userId + ", recordId=" + gameRecord.getId());
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
        System.out.println("Board state loaded successfully");

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

    @PostMapping("/deleteGame")
    public ResponseEntity<Map<String, Object>> deleteGame(@RequestBody Map<String, String> request, Authentication authentication) {
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

        gameRecordMapper.deleteById(recordId);
        System.out.println("Game deleted successfully: recordId=" + recordId);

        rabbitTemplate.convertAndSend("game-notifications", "Game deleted for user: " + username + ", recordId: " + recordId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanOldGameRecords() {
        Timestamp oneMonthAgo = new Timestamp(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
        gameRecordMapper.delete(
                new QueryWrapper<GameRecord>().lt("created_at", oneMonthAgo)
        );
        System.out.println("Cleaned old game records before: " + oneMonthAgo);
        rabbitTemplate.convertAndSend("game-notifications", "Cleaned old game records before: " + oneMonthAgo);
    }

    private Long getUserIdByUsername(String username) {
        try {
            Long userId = userMapper.findUserIdByUsername(username);
            if (userId == null) {
                System.out.println("User not found in users table: " + username);
            }
            return userId;
        } catch (Exception e) {
            System.out.println("Error fetching user_id for username: " + username);
            return null;
        }
    }
}