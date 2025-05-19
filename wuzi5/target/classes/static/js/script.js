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
    let board = [];
    let currentPlayer = 1; // Ensure Black starts first
    let aiMode = false;
    let gameOver = false;

    // Disable buttons for unauthenticated users
    const isAuthenticated = !document.getElementById('saveGame').hasAttribute('disabled');
    if (!isAuthenticated) {
        console.log('User not authenticated, disabling save/load buttons');
        saveGameButton.disabled = true;
        loadGameButton.disabled = true;
        saveGameButton.title = '请先登录';
        loadGameButton.title = '请先登录';
    }

    // Initialize board
    function initBoard(boardData) {
        console.log('Initializing board with data:', boardData);
        if (!gameBoard) {
            console.error('gameBoard element not found');
            return;
        }
        gameBoard.innerHTML = '';
        board = boardData || Array(15).fill().map(() => Array(15).fill('0'));
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
                cell.addEventListener('click', () => makeMove(i, j));
                gameBoard.appendChild(cell);
            }
        }
        console.log('Board initialized with', gameBoard.childElementCount, 'cells');
    }

    // Make a move
    function makeMove(row, col) {
        if (gameOver || board[row][col] !== '0') {
            console.log('Move blocked: gameOver=', gameOver, 'cell=', board[row][col]);
            return;
        }
        console.log('Making move at row:', row, 'col:', col);
        fetch('/makeMove', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ row, col })
        })
            .then(response => {
                console.log('Move response status:', response.status);
                return response.json();
            })
            .then(data => {
                console.log('Move response:', data);
                if (data.success) {
                    console.log('Setting piece at [', row, ',', col, '] to', data.piece);
                    board[row][col] = data.piece; // Use the piece directly from the server
                    console.log('Board state after move:', board[row][col], 'at [', row, ',', col, ']');
                    updateBoard();
                    currentPlayer = data.player;
                    console.log('Updated currentPlayer to:', currentPlayer);
                    if (data.win) {
                        alert(`Player ${currentPlayer === 1 ? 2 : 1} wins!`);
                        gameOver = true;
                    }
                    if (data.aiRow !== undefined && data.aiCol !== undefined) {
                        console.log('AI move at [', data.aiRow, ',', data.aiCol, '] to', 'O');
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

    // Update board display
    function updateBoard() {
        console.log('Updating board display');
        const cells = document.querySelectorAll('.cell');
        cells.forEach(cell => {
            const row = parseInt(cell.dataset.row);
            const col = parseInt(cell.dataset.col);
            cell.classList.remove('black', 'white');
            if (board[row][col] === 'X') {
                cell.classList.add('black');
                console.log('Rendered black piece at [', row, ',', col, ']');
            } else if (board[row][col] === 'O') {
                cell.classList.add('white');
                console.log('Rendered white piece at [', row, ',', col, ']');
            }
        });
    }

    // Reset game
    resetGameButton.addEventListener('click', () => {
        console.log('Resetting game');
        fetch('/resetGame', { method: 'POST' })
            .then(response => response.json())
            .then(data => {
                console.log('Reset response:', data);
                if (data.success) {
                    initBoard();
                    currentPlayer = 1; // Ensure Black starts first on reset
                    gameOver = false;
                    aiMode = false;
                    aiModeStatus.textContent = '关闭';
                }
            })
            .catch(error => console.error('Error resetting game:', error));
    });

    // Toggle AI mode
    toggleAIModeButton.addEventListener('click', () => {
        console.log('Toggling AI mode');
        fetch('/toggleAIMode', { method: 'POST' })
            .then(response => response.json())
            .then(data => {
                console.log('Toggle AI response:', data);
                if (data.success) {
                    aiMode = data.aiMode;
                    aiModeStatus.textText = aiMode ? '开启' : '关闭';
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

    // PVP mode
    pvpModeButton.addEventListener('click', () => {
        console.log('Switching to PVP mode');
        if (aiMode) {
            toggleAIModeButton.click();
        }
    });

    // Save game
    saveGameButton.addEventListener('click', () => {
        console.log('Saving game');
        fetch('/saveGame', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ moveHistory: '' })
        })
            .then(response => response.json())
            .then(data => {
                console.log('Save game response:', data);
                if (data.success) {
                    alert('Game saved successfully! Record ID: ' + data.recordId);
                    loadGameButton.click();
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error saving game:', error));
    });

    // Load game
    loadGameButton.addEventListener('click', () => {
        console.log('Loading game records');
        fetch('/listGames')
            .then(response => {
                console.log('List games response status:', response.status);
                return response.json();
            })
            .then(records => {
                console.log('Game records:', records);
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

    // Load game record
    window.loadGameRecord = function(recordId) {
        console.log('Loading game record:', recordId);
        fetch('/loadGame', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ recordId })
        })
            .then(response => response.json())
            .then(data => {
                console.log('Load game response:', data);
                if (data.success) {
                    board = data.board;
                    currentPlayer = data.player;
                    gameOver = data.gameOver;
                    aiMode = data.aiMode;
                    aiModeStatus.textContent = aiMode ? '开启' : '关闭';
                    initBoard(board);
                    gameRecordsModal.style.display = 'none';
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error loading game:', error));
    };

    // Delete game record
    window.deleteGameRecord = function(recordId) {
        console.log('Deleting game record:', recordId);
        fetch('/deleteGame', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ recordId })
        })
            .then(response => response.json())
            .then(data => {
                console.log('Delete game response:', data);
                if (data.success) {
                    alert('Game record deleted!');
                    loadGameButton.click();
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error deleting game:', error));
    };

    // Close modal
    closeModalButton.addEventListener('click', () => {
        console.log('Closing modal');
        gameRecordsModal.style.display = 'none';
    });

    // Initialize board on page load
    try {
        initBoard();
    } catch (error) {
        console.error('Error initializing board:', error);
    }
});