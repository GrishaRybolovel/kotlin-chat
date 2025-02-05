import React, { useEffect, useState } from 'react';

function ChatList({ token, onSelectChat }) {
  const [chats, setChats] = useState([]);
  const [newChatName, setNewChatName] = useState('');

  // Fetch chat rooms
  useEffect(() => {
    fetch('http://localhost:8080/chats', {
      headers: {
        'Authorization': `Bearer ${token}`  // if you add authentication headers
      }
    })
      .then(res => res.json())
      .then(data => setChats(data))
      .catch(err => console.error('Error fetching chats:', err));
  }, [token]);

  const createChat = () => {
    if (!newChatName.trim()) return;
    // You can POST as form data or JSON.
    const formData = new URLSearchParams();
    formData.append('name', newChatName);
    
    fetch('http://localhost:8080/chats', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',  // or application/json if backend expects JSON
        'Authorization': `Bearer ${token}`
      },
      body: formData.toString()
    })
      .then(res => res.json())
      .then(chat => {
        setChats(prev => [...prev, chat]);
        setNewChatName('');
      })
      .catch(err => console.error('Error creating chat:', err));
  };

  return (
    <div>
      <h2>Available Chat Rooms</h2>
      <ul>
        {chats.map(chat => (
          <li key={chat.id}>
            {chat.name} 
            <button onClick={() => onSelectChat(chat.id.toString())}>Join</button>
          </li>
        ))}
      </ul>
      <div>
        <h3>Create a New Chat Room</h3>
        <input 
          type="text" 
          value={newChatName} 
          onChange={e => setNewChatName(e.target.value)} 
          placeholder="Chat room name" 
        />
        <button onClick={createChat}>Create</button>
      </div>
    </div>
  );
}

export default ChatList;
