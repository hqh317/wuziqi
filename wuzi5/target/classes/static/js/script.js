document.addEventListener('DOMContentLoaded', function() {
    console.log('Script loaded, initializing board...');
    const gameBoard = document.getElementById('gameBoard');
    const resetGameButton = document.getElementById('resetGame');
    const toggleAIModeButton = document.getElementById('toggleAIMode');
    const aiModeStatus = document.getElementById('aiModeStatus');
    const pvpModeButton = document.getElementById('pvpMode');
    const saveGameButton = document.getElementById('saveGame');
    const loadGameButton = document.getElementById('loadGame');
    const gameRecordsModal = document.getElementById('gameRecordsModal');
    const gameRecordsTableBody = document.querySelector('#gameRecordsTable tbody');
    const closeModalButton = document.getElementById('closeModal');
    const spectateButton = document.getElementById('spectateButton');
    const activeGamesModal = document.getElementById('activeGamesModal');
    const activeGamesTableBody = document.querySelector('#activeGamesTable tbody');
    const closeActiveGamesModal = document.getElementById('closeActiveGamesModal');
    const spectatorMessage = document.getElementById('spectatorMessage');
    const spectatedUserSpan = document.getElementById('spectatedUser');
    const exitSpectatorModeButton = document.getElementById('exitSpectatorMode');
    const listTasksButton = document.getElementById('listTasks');
    const tasksModal = document.getElementById('tasksModal');
    const tasksTableBody = document.querySelector('#tasksTable tbody');
    const closeTasksModal = document.getElementById('closeTasksModal');
    let board = [];
    let currentPlayer = 1;
    let aiMode = false;
    let gameOver = false;
    let gameId = document.getElementById('gameId') ? document.getElementById('gameId').value : null;
    let isSpectator = false;
    let spectatedGameId = null;
    let stompClient = null;

    const isAuthenticated = !document.getElementById('saveGame').hasAttribute('disabled');
    const isAdmin = document.getElementById('isAdmin') ? document.getElementById('isAdmin').value === 'true' : false;

    console.log('isAuthenticated:', isAuthenticated, 'isAdmin:', isAdmin, 'gameId:', gameId);

    if (!isAuthenticated) {
        console.log('User not authenticated, disabling save/load buttons');
        saveGameButton.disabled = true;
        loadGameButton.disabled = true;
        saveGameButton.title = '请先登录';
        loadGameButton.title = '请先登录';
        listTasksButton.disabled = true;
        listTasksButton.title = '请先登录';
    }

    function initBoard(boardData) {
        console.log('Initializing board with data:', boardData);
        if (!gameBoard || !gameBoard.appendChild) {
            console.error('gameBoard element not found or invalid:', gameBoard);
            return;
        }
        gameBoard.innerHTML = '';
        board = boardData || Array(15).fill().map(() => Array(15).fill('0'));
        console.log('Board array created with dimensions:', board.length, 'x', board[0].length);
        for (let i = 0; i < 15; i++) {
            for (let j = 0; j < 15; j++) {
                const cell = document.createElement('div');
                cell.classList.add('cell');
                cell.dataset.row = i;
                cell.dataset.col = j;
                if (board[i][j] === 'X') {
                    cell.classList.add('black');
                } else if (board[i][j] === 'O') {
                    cell.classList.add('white');
                }
                console.log(`Adding cell at [${i}, ${j}], isSpectator: ${isSpectator}, isAdmin: ${isAdmin}`);
                if (!isSpectator) {
                    cell.addEventListener('click', () => makeMove(i, j));
                }
                gameBoard.appendChild(cell);
            }
        }
        console.log('Board initialized with', gameBoard.childElementCount, 'cells');
        if (gameBoard.childElementCount !== 225) {
            console.error('Board initialization failed: incorrect number of cells, got:', gameBoard.childElementCount);
        } else {
            console.log('Board initialization successful');
        }
    }

    function connectWebSocket(gameIdToSpectate) {
        if (stompClient) {
            stompClient.disconnect();
        }
        const socket = new SockJS('/ws');
        stompClient = Stomp.over(socket);
        spectatedGameId = gameIdToSpectate;
        stompClient.connect({}, function(frame) {
            console.log('Connected to WebSocket for gameId: ' + gameIdToSpectate + ', frame: ' + frame);
            stompClient.subscribe('/topic/game/' + gameIdToSpectate, function(message) {
                console.log('Received message for gameId: ' + gameIdToSpectate + ', message: ' + message.body);
                const gameState = JSON.parse(message.body);
                board = gameState.board.map(row => [...row]);
                currentPlayer = gameState.player;
                gameOver = gameState.gameOver;
                aiMode = gameState.aiMode;
                aiModeStatus.textContent = aiMode ? '开启' : '关闭';
                initBoard(board);
            });
        }, function(error) {
            console.error('WebSocket connection error for gameId: ' + gameIdToSpectate + ', error: ' + error);
        });
    }

    function toggleSpectatorMode(enable, username, gameIdToSpectate) {
        isSpectator = enable;
        console.log('Toggling spectator mode:', enable, 'for user:', username);
        if (enable) {
            resetGameButton.disabled = true;
            toggleAIModeButton.disabled = true;
            pvpModeButton.disabled = true;
            saveGameButton.disabled = true;
            loadGameButton.disabled = true;
            spectateButton.disabled = true;
            listTasksButton.disabled = true;
            spectatorMessage.style.display = 'block';
            spectatedUserSpan.textContent = username;
            connectWebSocket(gameIdToSpectate);
        } else {
            resetGameButton.disabled = false;
            toggleAIModeButton.disabled = false;
            pvpModeButton.disabled = false;
            saveGameButton.disabled = !isAuthenticated;
            loadGameButton.disabled = !isAuthenticated;
            spectateButton.disabled = false;
            listTasksButton.disabled = !isAuthenticated;
            spectatorMessage.style.display = 'none';
            spectatedUserSpan.textContent = '';
            if (stompClient) {
                stompClient.disconnect();
                stompClient = null;
            }
            initBoard();
            currentPlayer = 1;
            gameOver = false;
            aiMode = false;
            aiModeStatus.textContent = '关闭';
        }
    }

    function makeMove(row, col) {
        if (gameOver || board[row][col] !== '0' || isSpectator) return;
        console.log('Making move at row:', row, 'col:', col);
        fetch('/makeMove', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ row, col, gameId })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    board[row][col] = data.piece;
                    updateBoard();
                    currentPlayer = data.player;
                    if (data.win) {
                        alert(`Player ${currentPlayer === 1 ? 2 : 1} wins!`);
                        gameOver = true;
                    }
                    if (data.aiRow !== undefined && data.aiCol !== undefined) {
                        board[data.aiRow][data.aiCol] = 'O';
                        updateBoard();
                        currentPlayer = data.player;
                        if (data.aiWin) {
                            alert('AI wins!');
                            gameOver = true;
                        }
                    }
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error making move:', error));
    }

    function updateBoard() {
        const cells = document.querySelectorAll('.cell');
        cells.forEach(cell => {
            const row = parseInt(cell.dataset.row);
            const col = parseInt(cell.dataset.col);
            cell.classList.remove('black', 'white');
            if (board[row][col] === 'X') cell.classList.add('black');
            else if (board[row][col] === 'O') cell.classList.add('white');
        });
    }

    spectateButton.addEventListener('click', () => {
        fetch('/activeGames')
            .then(response => response.json())
            .then(data => {
                if (!data.success) {
                    alert(data.message);
                    return;
                }
                activeGamesTableBody.innerHTML = '';
                data.games.forEach(game => {
                    const row = document.createElement('tr');
                    row.innerHTML = `<td>${game.gameId}</td><td>${game.username}</td><td><button onclick="spectateGame('${game.gameId}', '${game.username}')">观战</button></td>`;
                    activeGamesTableBody.appendChild(row);
                });
                activeGamesModal.style.display = 'block';
            })
            .catch(error => console.error('Error listing active games:', error));
    });

    window.spectateGame = function(gameIdToSpectate, username) {
        toggleSpectatorMode(true, username, gameIdToSpectate);
        activeGamesModal.style.display = 'none';
    };

    exitSpectatorModeButton.addEventListener('click', () => toggleSpectatorMode(false));

    closeActiveGamesModal.addEventListener('click', () => {
        activeGamesModal.style.display = 'none';
    });

    resetGameButton.addEventListener('click', () => {
        fetch('/resetGame', { method: 'POST' })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    initBoard();
                    currentPlayer = 1;
                    gameOver = false;
                    aiMode = false;
                    aiModeStatus.textContent = '关闭';
                }
            })
            .catch(error => console.error('Error resetting game:', error));
    });

    toggleAIModeButton.addEventListener('click', () => {
        fetch('/toggleAIMode', { method: 'POST' })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    aiMode = data.aiMode;
                    aiModeStatus.textContent = aiMode ? '开启' : '关闭';
                    if (data.aiRow !== undefined && data.aiCol !== undefined) {
                        board[data.aiRow][data.aiCol] = 'O';
                        updateBoard();
                        currentPlayer = data.player;
                        if (data.aiWin) {
                            alert('AI wins!');
                            gameOver = true;
                        }
                    }
                }
            })
            .catch(error => console.error('Error toggling AI mode:', error));
    });

    pvpModeButton.addEventListener('click', () => {
        if (aiMode) toggleAIModeButton.click();
    });

    saveGameButton.addEventListener('click', () => {
        fetch('/saveGame', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ moveHistory: '' })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    alert('Game saved successfully! Record ID: ' + data.recordId);
                    loadGameButton.click();
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error saving game:', error));
    });

    loadGameButton.addEventListener('click', () => {
        fetch('/listGames')
            .then(response => response.json())
            .then(records => {
                gameRecordsTableBody.innerHTML = '';
                records.forEach(record => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td>${record.createdAt}</td>
                        <td>${record.aiMode ? 'AI' : 'PVP'}</td>
                        <td>Player ${record.currentPlayer}</td>
                        <td>${record.gameOver ? '已结束' : '进行中'}</td>
                        <td>
                            <button onclick="loadGameRecord('${record.id}')">加载</button>
                            <button onclick="deleteGameRecord('${record.id}')">删除</button>
                        </td>
                    `;
                    gameRecordsTableBody.appendChild(row);
                });
                gameRecordsModal.style.display = 'block';
            })
            .catch(error => console.error('Error listing games:', error));
    });

    window.loadGameRecord = function(recordId) {
        fetch('/loadGame', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ recordId })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    board = data.board;
                    currentPlayer = data.player;
                    gameOver = data.gameOver;
                    aiMode = data.aiMode;
                    aiModeStatus.textContent = aiMode ? '开启' : '关闭';
                    initBoard(board);
                    gameRecordsModal.style.display = 'none';
                    alert('Game loaded successfully! Record ID: ' + data.recordId);
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error loading game:', error));
    };

    window.deleteGameRecord = function(recordId) {
        fetch('/deleteGame', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ recordId })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    alert('Game record deleted!');
                    loadGameButton.click();
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error deleting game:', error));
    };

    closeModalButton.addEventListener('click', () => {
        gameRecordsModal.style.display = 'none';
    });

    listTasksButton.addEventListener('click', () => {
        fetch('/listTasks')
            .then(response => response.json())
            .then(tasks => {
                tasksTableBody.innerHTML = '';
                tasks.forEach(task => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td>${task.id}</td>
                        <td>${task.description}</td>
                        <td>${task.status}</td>
                        <td>${task.deadline}</td>
                        <td>
                            <button onclick="completeTask(${task.id})" ${task.status === 'COMPLETED' ? 'disabled' : ''}>完成</button>
                        </td>
                    `;
                    tasksTableBody.appendChild(row);
                });
                tasksModal.style.display = 'block';
            })
            .catch(error => console.error('Error listing tasks:', error));
    });

    window.completeTask = function(taskId) {
        fetch('/completeTask', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ taskId })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    alert('任务已完成！');
                    listTasksButton.click();
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error completing task:', error));
    };

    closeTasksModal.addEventListener('click', () => {
        tasksModal.style.display = 'none';
    });

    try {
        initBoard();
        if (isAdmin) {
            console.log('Admin user detected, ensuring board visibility');
            initBoard(); // 再次调用以确保棋盘加载
        }
    } catch (error) {
        console.error('Error initializing board:', error);
    }

    // 额外检查：如果棋盘未显示，尝试延迟初始化
    setTimeout(() => {
        if (gameBoard.childElementCount !== 225) {
            console.log('Board not initialized, retrying...');
            initBoard();
        }
    }, 500);
});