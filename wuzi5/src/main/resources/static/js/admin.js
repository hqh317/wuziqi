document.addEventListener('DOMContentLoaded', function() {
    const createTaskButton = document.getElementById('createTask');
    const createTaskModal = document.getElementById('createTaskModal');
    const submitTaskButton = document.getElementById('submitTask');
    const cancelCreateTaskButton = document.getElementById('cancelCreateTask');
    const listUsersButton = document.getElementById('listUsers');
    const usersModal = document.getElementById('usersModal');
    const closeUsersModalButton = document.getElementById('closeUsersModal');
    const listAllTasksButton = document.getElementById('listAllTasks');
    const tasksModal = document.getElementById('tasksModal');
    const closeTasksModalButton = document.getElementById('closeTasksModal');
    const assignUserSelect = document.getElementById('assignUser');

    createTaskButton.addEventListener('click', () => {
        createTaskModal.style.display = 'block';
        fetch('/users')
            .then(response => response.json())
            .then(users => {
                assignUserSelect.innerHTML = '';
                users.forEach(user => {
                    const option = document.createElement('option');
                    option.value = user.id;
                    option.textContent = user.username;
                    assignUserSelect.appendChild(option);
                });
            })
            .catch(error => console.error('Error fetching users:', error));
    });

    submitTaskButton.addEventListener('click', () => {
        const description = document.getElementById('taskDescription').value;
        const deadlineInput = document.getElementById('taskDeadline');
        const deadline = deadlineInput.getAttribute('data-formatted') || deadlineInput.value;
        const userId = assignUserSelect.value;

        const deadlinePattern = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01]) (0\d|1\d|2[0-3]):([0-5]\d):([0-5]\d)$/;
        if (!deadlinePattern.test(deadline)) {
            document.getElementById('deadlineError').style.display = 'block';
            return;
        }

        fetch('/createTask', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ description, deadline, userId })
        })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    alert('任务创建成功！');
                    createTaskModal.style.display = 'none';
                    document.getElementById('taskDescription').value = '';
                    document.getElementById('taskDeadline').value = '';
                    deadlineInput.removeAttribute('data-formatted');
                } else {
                    alert(data.message);
                }
            })
            .catch(error => console.error('Error creating task:', error));
    });

    cancelCreateTaskButton.addEventListener('click', () => {
        createTaskModal.style.display = 'none';
        document.getElementById('taskDescription').value = '';
        document.getElementById('taskDeadline').value = '';
        document.getElementById('taskDeadline').removeAttribute('data-formatted');
        document.getElementById('deadlineError').style.display = 'none';
    });

    listUsersButton.addEventListener('click', () => {
        usersModal.style.display = 'block';
        fetch('/users')
            .then(response => response.json())
            .then(users => {
                const tableBody = document.querySelector('#usersTable tbody');
                tableBody.innerHTML = '';
                users.forEach(user => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td>${user.id}</td>
                        <td>${user.username}</td>
                        <td>${user.role}</td>
                        <td><button onclick="deleteUser(${user.id})">删除</button></td>
                    `;
                    tableBody.appendChild(row);
                });
            })
            .catch(error => console.error('Error listing users:', error));
    });

    closeUsersModalButton.addEventListener('click', () => {
        usersModal.style.display = 'none';
    });

    listAllTasksButton.addEventListener('click', () => {
        tasksModal.style.display = 'block';
        fetch('/allTasks')
            .then(response => response.json())
            .then(tasks => {
                const tableBody = document.querySelector('#tasksTable tbody');
                tableBody.innerHTML = '';
                tasks.forEach(task => {
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td>${task.id}</td>
                        <td>${task.description}</td>
                        <td>${task.username}</td>
                        <td>${task.status}</td>
                        <td>${task.deadline}</td>
                    `;
                    tableBody.appendChild(row);
                });
            })
            .catch(error => console.error('Error listing tasks:', error));
    });

    closeTasksModalButton.addEventListener('click', () => {
        tasksModal.style.display = 'none';
    });

    window.deleteUser = function(userId) {
        if (confirm('确定删除该用户？')) {
            fetch(`/deleteUser/${userId}`, { method: 'DELETE' })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        alert('用户删除成功！');
                        listUsersButton.click();
                    } else {
                        alert(data.message);
                    }
                })
                .catch(error => console.error('Error deleting user:', error));
        }
    };
});