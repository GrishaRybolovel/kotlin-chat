// src/PersonalMessagesTab.js
import React, { useEffect, useState } from 'react';

function PersonalMessagesTab({ token, onSelectUser }) {
  const [users, setUsers] = useState([]);

  useEffect(() => {
    fetch("http://localhost:8080/users", {
      headers: { "Authorization": `Bearer ${token}` }
    })
      .then(response => response.json())
      .then(data => {
        // Optionally, filter out the logged in user.
        const loggedInUser = getUsernameFromToken(token);
        const filtered = data.filter(user => user.username !== loggedInUser);
        setUsers(filtered);
      })
      .catch(error => console.error("Error fetching users:", error));
  }, [token]);

  // Helper: simple JWT decode to get username (for demo purposes only).
  const getUsernameFromToken = (token) => {
    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload)).username;
    } catch (e) {
      return "";
    }
  };

  return (
    <div>
      <h2>Personal Messages</h2>
      <ul>
        {users.map((user, index) => (
          <li key={index}>
            {user.username} <button onClick={() => onSelectUser(user.username)}>Chat</button>
          </li>
        ))}
      </ul>
    </div>
  );
}

export default PersonalMessagesTab;
