// src/App.js
import React, { useState } from 'react';
import Login from './Login';
import Register from './Register';
import ChatList from './ChatList'; // Your public chat list component
import PersonalMessagesTab from './PersonalMessagesTab';
import PersonalChatRoom from './PersonalChatRoom';
import ChatRoom from './ChatRoom';

function App() {
  const [token, setToken] = useState(null);
  const [view, setView] = useState("login"); // "login", "register", or "app"
  const [currentTab, setCurrentTab] = useState("chats"); // "chats" or "personal"
  const [selectedPersonalUser, setSelectedPersonalUser] = useState(null);
  const [selectedChat, setSelectedChat] = useState(null);

  // If not logged in, show login/register views.
  if (!token) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        {view === "login" ? (
          <Login
            onLogin={(token) => { setToken(token); setView("app"); }}
            onSwitch={() => setView("register")}
          />
        ) : (
          <Register onRegister={() => setView("login")} onSwitch={() => setView("login")} />
        )}
      </div>
    );
  }

  // If a personal chat is selected, show the personal chat room.
  if (selectedPersonalUser) {
    return (
      <PersonalChatRoom
        token={token}
        partner={selectedPersonalUser}
        onBack={() => setSelectedPersonalUser(null)}
      />
    );
  }

  if (selectedChat) {
    return (
      <ChatRoom
        token={token}
        chatId={selectedChat}
        onBack={() => setSelectedChat(null)}
      />
    );
  }

  // Main view: display a nav for switching between tabs and show the corresponding component.
  return (
    <div>
      <nav style={{ marginBottom: "1rem" }}>
        <button onClick={() => setCurrentTab("chats")}>Chats</button>
        <button onClick={() => setCurrentTab("personal")}>Personal Messages</button>
      </nav>
      {currentTab === "chats" ? (
        <ChatList token={token} onSelectChat={(chatId) => {
          console.log("Chat selected:", chatId);
          setSelectedChat(chatId);
        }} />
      ) : (
        <PersonalMessagesTab token={token} onSelectUser={(username) => setSelectedPersonalUser(username)} />
      )}
    </div>
  );
}

export default App;
