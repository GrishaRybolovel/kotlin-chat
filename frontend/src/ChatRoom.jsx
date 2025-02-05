import React, { useState, useEffect, useRef } from 'react';

function ChatRoom({ token, chatId, onBack }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const ws = useRef(null);

  useEffect(() => {
    fetch(`http://localhost:8080/chat/${chatId}/history`)
      .then(response => response.json())
      .then(data => setMessages(data))
      .catch(error => console.error('Error loading chat history:', error));
  }, [chatId]);

  useEffect(() => {
    // Optionally, fetch chat history if needed (see previous instructions)
    // ...

    // Connect to the WebSocket endpoint. Ensure chatId is passed.
    const wsUrl = `ws://localhost:8080/chat/${chatId}?token=${token}`;
    ws.current = new WebSocket(wsUrl);

    ws.current.onopen = () => {
      console.log('Connected to chat', chatId);
    };

    ws.current.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        setMessages(prev => [...prev, message]);
      } catch (err) {
        console.error('Error parsing message:', err);
      }
    };

    ws.current.onerror = (err) => {
      console.error('WebSocket error:', err);
    };

    ws.current.onclose = () => {
      console.log('WebSocket connection closed');
    };

    return () => {
      if (ws.current) ws.current.close();
    };
  }, [token, chatId]);

  const sendMessage = () => {
    if (ws.current && input.trim() !== '') {
      ws.current.send(input);
      setInput('');
    }
  };

  return (
    <div>
      <button onClick={onBack}>Back to Chat List</button>
      <h2>Chat Room: {chatId}</h2>
      <div style={{ border: '1px solid #ccc', height: '300px', overflowY: 'scroll', padding: '10px', marginBottom: '10px' }}>
        {messages.map((msg, index) => (
          <div key={index}>
            <strong>{msg.sender}</strong>: {msg.text} <small>{new Date(msg.timestamp).toLocaleTimeString()}</small>
          </div>
        ))}
      </div>
      <div>
        <input 
          style={{ width: '70%' }} 
          value={input} 
          onChange={(e) => setInput(e.target.value)} 
          placeholder="Type your message..." 
        />
        <button onClick={sendMessage}>Send</button>
      </div>
    </div>
  );
}

export default ChatRoom;
