// src/PersonalChatRoom.js
import React, { useEffect, useState, useRef } from 'react';

function PersonalChatRoom({ token, partner, onBack }) {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const ws = useRef(null);

  // Fetch conversation history when component mounts
  useEffect(() => {
    fetch(`http://localhost:8080/personal/history/${partner}?token=${token}`)
      .then(res => res.json())
      .then(data => setMessages(data))
      .catch(err => console.error("Error loading conversation history:", err));
  }, [partner, token]);

  // Open WebSocket connection for personal messaging
  useEffect(() => {
    const wsUrl = `ws://localhost:8080/personal?token=${token}`;
    ws.current = new WebSocket(wsUrl);

    ws.current.onopen = () => {
      console.log("Connected to personal messages WebSocket");
    };

    ws.current.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        // Determine the logged-in username from the token.
        const username = getUsernameFromToken(token);
        // Only include messages that are part of the conversation with the selected partner.
        if ((msg.from === partner && msg.to === username) || (msg.from === username && msg.to === partner)) {
          setMessages(prev => [...prev, msg]);
        }
      } catch (err) {
        console.error("Error parsing incoming message:", err);
      }
    };

    ws.current.onerror = (err) => {
      console.error("WebSocket error:", err);
    };

    ws.current.onclose = () => {
      console.log("WebSocket connection closed");
    };

    return () => {
      if (ws.current) ws.current.close();
    };
  }, [partner, token]);

  const getUsernameFromToken = (token) => {
    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload)).username;
    } catch (e) {
      return "";
    }
  };

  const sendMessage = () => {
    if (ws.current && input.trim() !== '') {
      // Construct a JSON message that the backend expects for personal messaging.
      // Example format: { "to": "recipientUsername", "text": "message content" }
      const messageData = { to: partner, text: input };
      ws.current.send(JSON.stringify(messageData));
      setInput('');
    }
  };

  return (
    <div>
      <button onClick={onBack}>Back to User List</button>
      <h2>Chat with {partner}</h2>
      <div style={{
        border: "1px solid #ccc",
        height: "300px",
        overflowY: "scroll",
        padding: "10px",
        marginBottom: "10px"
      }}>
        {messages.map((msg, index) => (
          <div key={index}>
            <strong>{msg.from}</strong>: {msg.text} <small>{new Date(msg.timestamp).toLocaleTimeString()}</small>
          </div>
        ))}
      </div>
      <div>
        <input
          style={{ width: "70%" }}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Type your message..."
        />
        <button onClick={sendMessage}>Send</button>
      </div>
    </div>
  );
}

export default PersonalChatRoom;
