document.addEventListener('DOMContentLoaded', function() {
    console.log('Script loaded, initializing board...');
    const gameBoard = document.getElementById('gameBoard');
    if (!gameBoard) {
        console.error('gameBoard element not found in DOM');
        return;
    }

    // Query DOM elements with null checks
    const elements = {
        resetGameButton: document.getElementById('resetGame'),
        toggleAIModeButton: document.getElementById('toggleAIMode'),
        aiModeStatus: document.getElementById('aiModeStatus'),
        pvpModeButton: document.getElementById('pvpMode'),
        saveGameButton: document.getElementById('saveGame'),
        loadGameButton: document.getElementById('loadGame'),
        gameRecordsModal: document.getElementById('gameRecordsModal'),
        gameRecordsTableBody: document.querySelector('#gameRecordsTable tbody'),
        closeModalButton: document.getElementById('closeModal'),
        spectateButton: document.getElementById('spectateButton'),
        activeGamesModal: document.getElementById('activeGamesModal'),
        activeGamesTableBody: document.querySelector('#activeGamesTable tbody'),
        closeActiveGamesModal: document.getElementById('closeActiveGamesModal'),
        spectatorMessage: document.getElementById('spectatorMessage'),
        spectatedUserSpan: document.getElementById('spectatedUser'),
        exitSpectatorModeButton: document.getElementById('exitSpectatorMode'),
        listTasksButton: document.getElementById('listTasks'),
        tasksModal: document.getElementById('tasksModal'),
        tasksTableBody: document.querySelector('#tasksTable tbody'),
        closeTasksModal: document.getElementById('closeTasksModal')
    };

    // Log missing elements
    Object.keys(elements).forEach(key => {
        if (!elements[key]) {
            console.error(`${key} element not found in DOM`);
        }
    });

    // Initialize variables
    let board = [];
    let currentPlayer = 1;
    let aiMode = false;
    let gameOver = false;
    let gameId = document.getElementById('gameId') ? document.getElementById('gameId').value : null;
    let isSpectator = false;
    let spectatedGameId = null;
    let stompClient = null;

    const isAuthenticated = elements.saveGameButton && !elements.saveGameButton.hasAttribute('disabled');
    const isAdmin = window.isAdmin || false;

    console.log('isAuthenticated:', isAuthenticated, 'isAdmin:', isAdmin, 'gameId:', gameId);
    console.log('Initial board from server:', window.initialBoard);

    if (!isAuthenticated) {
        console.log('User not authenticated, disabling save/load buttons');
        if (elements.saveGameButton) elements.saveGameButton.disabled = true;
        if (elements.loadGameButton) elements.loadGameButton.disabled = true;
        if (elements.saveGameButton) elements.saveGameButton.title = '请先登录';
        if (elements.loadGameButton) elements.loadGameButton.title = '请先登录';
        if (elements.listTasksButton) elements.listTasksButton.disabled = true;
        if (elements.listTasksButton) elements.listTasksButton.title = '请先登录';
    }

    function initBoard(boardData) {
        console.log('Initializing board with data:', boardData);
        if (!gameBoard || !gameBoard.appendChild) {
            console.error('gameBoard element not found or invalid:', gameBoard);
            return;
        }
        gameBoard.innerHTML = '';
        board = boardData || Array(15).fill().map(() => Array(15).fill('0'));
        if (!board || board.length !== 15 || board[0].length !== 15) {
            console.error('Invalid board data, using default board');
            board = Array(15).fill().map(() => Array(15).fill('0'));
        }
        console.log('Board array dimensions:', board.length, 'x', board[0].length);
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

    // Attach event listeners with null checks
    if (elements.resetGameButton) {
        elements.resetGameButton.addEventListener('click', () => {
            fetch('/resetGame', { method: 'POST' })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        initBoard();
                        currentPlayer = 1;
                        gameOver = false;
                        aiMode = false;
                        if (elements.aiModeStatus) elements.aiModeStatus.textContent = '关闭';
                    }
                })
                .catch(error => console.error('Error resetting game:', error));
        });
    }

    if (elements.toggleAIModeButton) {
        elements.toggleAIModeButton.addEventListener('click', () => {
            fetch('/toggleAIMode', { method: 'POST' })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        aiMode = data.aiMode;
                        if (elements.aiModeStatus) elements.aiModeStatus.textContent = aiMode ? '开启' : '关闭';
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
    }

    if (elements.pvpModeButton) {
        elements.pvpModeButton.addEventListener('click', () => {
            if (aiMode && elements.toggleAIModeButton) elements.toggleAIModeButton.click();
        });
    }

    if (elements.saveGameButton) {
        elements.saveGameButton.addEventListener('click', () => {
            fetch('/saveGame', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ moveHistory: '' })
            })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        alert('Game saved successfully! Record ID: ' + data.recordId);
                        if (elements.loadGameButton) elements.loadGameButton.click();
                    } else {
                        alert(data.message);
                    }
                })
                .catch(error => console.error('Error saving game:', error));
        });
    }

    if (elements.loadGameButton) {
        elements.loadGameButton.addEventListener('click', () => {
            fetch('/listGames')
                .then(response => response.json())
                .then(records => {
                    if (elements.gameRecordsTableBody) {
                        elements.gameRecordsTableBody.innerHTML = '';
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
                            elements.gameRecordsTableBody.appendChild(row);
                        });
                        if (elements.gameRecordsModal) elements.gameRecordsModal.style.display = 'block';
                    }
                })
                .catch(error => console.error('Error listing games:', error));
        });
    }

    if (elements.spectateButton) {
        elements.spectateButton.addEventListener('click', () => {
            fetch('/activeGames')
                .then(response => response.json())
                .then(data => {
                    if (!data.success) {
                        alert(data.message);
                        return;
                    }
                    if (elements.activeGamesTableBody) {
                        elements.activeGamesTableBody.innerHTML = '';
                        data.games.forEach(game => {
                            const row = document.createElement('tr');
                            row.innerHTML = `<td>${game.gameId}</td><td>${game.username}</td><td><button onclick="spectateGame('${game.gameId}', '${game.username}')">观战</button></td>`;
                            elements.activeGamesTableBody.appendChild(row);
                        });
                        if (elements.activeGamesModal) elements.activeGamesModal.style.display = 'block';
                    }
                })
                .catch(error => console.error('Error listing active games:', error));
        });
    }

    if (elements.exitSpectatorModeButton) {
        elements.exitSpectatorModeButton.addEventListener('click', () => toggleSpectatorMode(false));
    }

    if (elements.closeActiveGamesModal) {
        elements.closeActiveGamesModal.addEventListener('click', () => {
            if (elements.activeGamesModal) elements.activeGamesModal.style.display = 'none';
        });
    }

    if (elements.closeModalButton) {
        elements.closeModalButton.addEventListener('click', () => {
            if (elements.gameRecordsModal) elements.gameRecordsModal.style.display = 'none';
        });
    }

    if (elements.listTasksButton) {
        elements.listTasksButton.addEventListener('click', () => {
            fetch('/listTasks')
                .then(response => response.json())
                .then(tasks => {
                    if (elements.tasksTableBody) {
                        elements.tasksTableBody.innerHTML = '';
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
                            elements.tasksTableBody.appendChild(row);
                        });
                        if (elements.tasksModal) elements.tasksModal.style.display = 'block';
                    }
                })
                .catch(error => console.error('Error listing tasks:', error));
        });
    }

    if (elements.closeTasksModal) {
        elements.closeTasksModal.addEventListener('click', () => {
            if (elements.tasksModal) elements.tasksModal.style.display = 'none';
        });
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
                if (elements.aiModeStatus) elements.aiModeStatus.textContent = aiMode ? '开启' : '关闭';
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
            if (elements.resetGameButton) elements.resetGameButton.disabled = true;
            if (elements.toggleAIModeButton) elements.toggleAIModeButton.disabled = true;
            if (elements.pvpModeButton) elements.pvpModeButton.disabled = true;
            if (elements.saveGameButton) elements.saveGameButton.disabled = true;
            if (elements.loadGameButton) elements.loadGameButton.disabled = true;
            if (elements.spectateButton) elements.spectateButton.disabled = true;
            if (elements.listTasksButton) elements.listTasksButton.disabled = true;
            if (elements.spectatorMessage) elements.spectatorMessage.style.display = 'block';
            if (elements.spectatedUserSpan) elements.spectatedUserSpan.textContent = username;
            connectWebSocket(gameIdToSpectate);
        } else {
            if (elements.resetGameButton) elements.resetGameButton.disabled = false;
            if (elements.toggleAIModeButton) elements.toggleAIModeButton.disabled = false;
            if (elements.pvpModeButton) elements.pvpModeButton.disabled = false;
            if (elements.saveGameButton) elements.saveGameButton.disabled = !isAuthenticated;
            if (elements.loadGameButton) elements.loadGameButton.disabled = !isAuthenticated;
            if (elements.spectateButton) elements.spectateButton.disabled = false;
            if (elements.listTasksButton) elements.listTasksButton.disabled = !isAuthenticated;
            if (elements.spectatorMessage) elements.spectatorMessage.style.display = 'none';
            if (elements.spectatedUserSpan) elements.spectatedUserSpan.textContent = '';
            if (stompClient) {
                stompClient.disconnect();
                stompClient = null;
            }
            initBoard();
            currentPlayer = 1;
            gameOver = false;
            aiMode = false;
            if (elements.aiModeStatus) elements.aiModeStatus.textContent = '关闭';
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

    window.spectateGame = function(gameIdToSpectate, username) {
        toggleSpectatorMode(true, username, gameIdToSpectate);
        if (elements.activeGamesModal) elements.activeGamesModal.style.display = 'none';
    };

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
                    if (elements.aiModeStatus) elements.aiModeStatus.textContent = aiMode ? '开启' : '关闭';
                    initBoard(board);
                    if (elements.gameRecordsModal) elements.gameRecordsModal.style.display = 'none';
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
                    if (elements.loadGameButton) elements.loadGameButton.click();
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error deleting game:', error));
    };

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
                    if (elements.listTasksButton) elements.listTasksButton.click();
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error completing task:', error));
    };

    try {
        initBoard(window.initialBoard); // Initialize with server-provided board
        if (isAdmin) {
            console.log('Admin user detected, re-initializing with server board');
            initBoard(window.initialBoard); // Ensure admin sees the board
        }
    } catch (error) {
        console.error('Error initializing board:', error);
        initBoard(); // Fallback to default board
    }
});
