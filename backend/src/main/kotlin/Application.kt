package com.example

// Ktor and Serialization imports
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*

// WebSocket imports (Ktor 2.3.0)
import io.ktor.server.websocket.*
import io.ktor.websocket.*

// JWT imports
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

// Exposed and Database imports
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.statements.InsertStatement

// Other imports
import java.time.Duration
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.GsonBuilder

// Import the CORS plugin from the correct package:
import io.ktor.server.plugins.cors.CORS

// ──────────────────────────────────────────────
// Data Classes
// ──────────────────────────────────────────────

data class RegisterRequest(val username: String, val password: String)
data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String)
data class ChatMessage(val sender: String, val text: String, val timestamp: Long)
data class PersonalMessageRequest(val to: String, val text: String)

// ──────────────────────────────────────────────
// Exposed Table Definitions
// ──────────────────────────────────────────────

object Users : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    // WARNING: Plaintext passwords are used here only for demonstration.
    val password = varchar("password", 64)
    override val primaryKey = PrimaryKey(id, name = "PK_User_ID")
}

// New table for storing chat messages
object ChatMessages : Table() {
    val id = integer("id").autoIncrement()
    // The chat room identifier (e.g., "general", "random", etc.)
    val chatId = varchar("chat_id", 50)
    val sender = varchar("sender", 50)
    val text = text("text")
    val timestamp = long("timestamp")
    override val primaryKey = PrimaryKey(id, name = "PK_Message_ID")
}

object ChatRooms : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    override val primaryKey = PrimaryKey(id, name = "PK_ChatRoom_ID")
}

object PersonalMessages : Table() {
    val id = integer("id").autoIncrement()
    val sender = varchar("sender", 50)
    val recipient = varchar("recipient", 50)
    val text = text("text")
    val timestamp = long("timestamp")
    override val primaryKey = PrimaryKey(id, name = "PK_PersonalMessage_ID")
}

// ──────────────────────────────────────────────
// Database Initialization
// ──────────────────────────────────────────────

fun initDatabase() {
    val dbUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://db:5432/chatapp"
    val dbUser = System.getenv("DATABASE_USER") ?: "chatuser"
    val dbPassword = System.getenv("DATABASE_PASSWORD") ?: "chatpassword"
    
    Database.connect(
        url = dbUrl,
        driver = "org.postgresql.Driver",
        user = dbUser,
        password = dbPassword
    )
    transaction {
        SchemaUtils.create(Users, ChatMessages, ChatRooms, PersonalMessages)
    }
}

// ──────────────────────────────────────────────
// JWT Token Generation
// ──────────────────────────────────────────────

fun generateToken(username: String): String {
    val secret = "secret"  // In production, load this securely
    val algorithm = Algorithm.HMAC256(secret)
    val expirationTimeInMillis = 3600_000 // 1 hour
    return JWT.create()
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + expirationTimeInMillis))
        .sign(algorithm)
}

// ──────────────────────────────────────────────
// Global Gson Instance
// ──────────────────────────────────────────────

val gson = GsonBuilder().setPrettyPrinting().create()

// ──────────────────────────────────────────────
// In-Memory Chat Sessions Storage
// Maps a chat room ID to a list of active WebSocket sessions.
val chatRoomSessions = ConcurrentHashMap<String, MutableList<DefaultWebSocketServerSession>>()
val personalSessions = ConcurrentHashMap<String, MutableList<DefaultWebSocketServerSession>>()

// ──────────────────────────────────────────────
// Main Entry Point
// ──────────────────────────────────────────────

fun main() {
    initDatabase()
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(wait = true)
}

// ──────────────────────────────────────────────
// Application Module
// ──────────────────────────────────────────────

fun Application.module() {
    install(CORS) {
    allowHost("localhost:3000", schemes = listOf("http"))

    // Allow specific headers:
    allowHeader(HttpHeaders.ContentType)
    allowHeader(HttpHeaders.Authorization)

    // Allow common HTTP methods:
    allowMethod(HttpMethod.Get)
    allowMethod(HttpMethod.Post)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Options)

    // Optionally, set the maximum age for preflight requests:
    maxAgeInSeconds = 3600
}

    // Install content negotiation with Gson
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }
    
    // Install WebSockets with basic settings
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    routing {
        // ───────── Registration Endpoint ─────────
        post("/register") {
            val registerRequest = call.receive<RegisterRequest>()
            val userExists = transaction {
                Users.select { Users.username eq registerRequest.username }.any()
            }
            if (userExists) {
                call.respondText("Username already exists", status = HttpStatusCode.Conflict)
            } else {
                transaction {
                    Users.insert { row: InsertStatement<Number> ->
                        row[username] = registerRequest.username
                        row[password] = registerRequest.password
                    }
                }
                call.respondText("User registered successfully", status = HttpStatusCode.Created)
            }
        }
        
        // ───────── Login Endpoint ─────────
        post("/login") {
            val loginRequest = call.receive<LoginRequest>()
            val userRecord = transaction {
                Users.select { Users.username eq loginRequest.username }.singleOrNull()
            }
            if (userRecord == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
            } else {
                val storedPassword = userRecord[Users.password]
                if (storedPassword != loginRequest.password) {
                    call.respondText("Invalid credentials", status = HttpStatusCode.Unauthorized)
                } else {
                    val token = generateToken(loginRequest.username)
                    call.respond(LoginResponse(token))
                }
            }
        }

        get("/users") {
            val users = transaction {
                Users.selectAll().map { mapOf("username" to it[Users.username]) }
            }
            call.respond(users)
        }

        get("/chats") {
            val rooms = transaction {
                ChatRooms.selectAll().orderBy(ChatRooms.id to SortOrder.ASC).map {
                    // Convert each row into a simple map or data class. For simplicity, we use a map.
                    mapOf(
                        "id" to it[ChatRooms.id],
                        "name" to it[ChatRooms.name]
                    )
                }
            }
            call.respond(rooms)
        }
        
        // POST /chats – create a new chat room
        post("/chats") {
            // Expect a JSON body containing a "name" for the new chat room.
            val parameters = call.receiveParameters() // or use call.receive<Map<String, String>>() if you prefer JSON
            val name = parameters["name"] ?: return@post call.respondText(
                "Missing chat room name",
                status = HttpStatusCode.BadRequest
            )
            // Insert new chat room and return its id and name.
            val newId = transaction {
                ChatRooms.insert {
                    it[ChatRooms.name] = name
                } get ChatRooms.id
            }
            call.respond(mapOf("id" to newId, "name" to name))
        }

        get("/chat/{chatId}/history") {
            val chatId = call.parameters["chatId"]
            if (chatId == null) {
                call.respond(HttpStatusCode.BadRequest, "Chat room not specified")
                return@get
            }
            // Retrieve messages for the chat room from the database
            val messages = transaction {
                ChatMessages.select { ChatMessages.chatId eq chatId }
                    .orderBy(ChatMessages.timestamp to SortOrder.ASC)
                    .map {
                        ChatMessage(
                            sender = it[ChatMessages.sender],
                            text = it[ChatMessages.text],
                            timestamp = it[ChatMessages.timestamp]
                        )
                    }
            }
            call.respond(messages)
        }
        
        // ───────── WebSocket Chat Endpoint ─────────
        // Clients connect using: ws://localhost:8080/chat/{chatId}?token=YOUR_JWT_TOKEN
        webSocket("/chat/{chatId}") {
            // Validate token from query parameters
            val tokenParam = call.request.queryParameters["token"]
            if (tokenParam == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No token provided"))
                return@webSocket
            }
            val secret = "secret"
            val algorithm = Algorithm.HMAC256(secret)
            val username = try {
                val verifier = JWT.require(algorithm).build()
                verifier.verify(tokenParam).getClaim("username").asString()
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }
            // Get chat room ID from URL parameter
            val chatId = call.parameters["chatId"]
            if (chatId == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "No chat room specified"))
                return@webSocket
            }
            // Add this WebSocket session to the chat room
            val sessions = chatRoomSessions.computeIfAbsent(chatId) { mutableListOf() }
            sessions.add(this)
            try {
                // Process incoming frames
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val timestamp = System.currentTimeMillis()
                        val message = ChatMessage(username, text, timestamp)
                        val jsonMessage = gson.toJson(message)
                        
                        // Store the message in the database
                        transaction {
                            ChatMessages.insert { row: InsertStatement<Number> ->
                                row[ChatMessages.chatId] = chatId
                                row[ChatMessages.sender] = username
                                row[ChatMessages.text] = text
                                row[ChatMessages.timestamp] = timestamp
                            }
                        }
                        
                        // Broadcast the message to all sessions in the chat room
                        sessions.forEach { session ->
                            session.send(Frame.Text(jsonMessage))
                        }
                    }
                }
            } catch (e: Exception) {
                // Optionally log the error
            } finally {
                sessions.remove(this)
            }
        }

        get("/personal/history/{with}") {
            // "with" is the username of the conversation partner
            val withUser = call.parameters["with"]
            val tokenParam = call.request.queryParameters["token"]
            if (withUser == null || tokenParam == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing parameters")
                return@get
            }
            val secret = "secret"
            val algorithm = Algorithm.HMAC256(secret)
            val username = try {
                val verifier = JWT.require(algorithm).build()
                verifier.verify(tokenParam).getClaim("username").asString()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                return@get
            }
            // Retrieve messages between the two users
            val messages = transaction {
                PersonalMessages.select {
                    ((PersonalMessages.sender eq username) and (PersonalMessages.recipient eq withUser)) or
                    ((PersonalMessages.sender eq withUser) and (PersonalMessages.recipient eq username))
                }.orderBy(PersonalMessages.timestamp to SortOrder.ASC)
                 .map {
                     mapOf(
                         "from" to it[PersonalMessages.sender],
                         "to" to it[PersonalMessages.recipient],
                         "text" to it[PersonalMessages.text],
                         "timestamp" to it[PersonalMessages.timestamp]
                     )
                 }
            }
            call.respond(messages)
        }

        webSocket("/personal") {
            // Validate the JWT token from the query parameter
            val tokenParam = call.request.queryParameters["token"]
            if (tokenParam == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No token provided"))
                return@webSocket
            }
            val secret = "secret"
            val algorithm = Algorithm.HMAC256(secret)
            val senderUsername = try {
                val verifier = JWT.require(algorithm).build()
                verifier.verify(tokenParam).getClaim("username").asString()
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }
            
            // Add this session to the personalSessions map for the sender
            val sessions = personalSessions.computeIfAbsent(senderUsername) { mutableListOf() }
            sessions.add(this)
            
            try {
                // Process incoming frames
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()
                        
                        // Parse the incoming JSON message
                        val personalMsg = try {
                            gson.fromJson(receivedText, PersonalMessageRequest::class.java)
                        } catch (e: Exception) {
                            // If parsing fails, ignore this message
                            continue
                        }
                        
                        val timestamp = System.currentTimeMillis()
                        
                        // Store the personal message in the database
                        transaction {
                            PersonalMessages.insert { row: InsertStatement<Number> ->
                                row[PersonalMessages.sender] = senderUsername
                                row[PersonalMessages.recipient] = personalMsg.to
                                row[PersonalMessages.text] = personalMsg.text
                                row[PersonalMessages.timestamp] = timestamp
                            }
                        }
                        
                        // Create a JSON response to send
                        val jsonResponse = gson.toJson(
                            mapOf(
                                "from" to senderUsername,
                                "to" to personalMsg.to,
                                "text" to personalMsg.text,
                                "timestamp" to timestamp
                            )
                        )
                        
                        // Deliver the message to the recipient if they are online
                        personalSessions[personalMsg.to]?.forEach { recipientSession ->
                            recipientSession.send(Frame.Text(jsonResponse))
                        }
                        
                        // Optionally, echo back the message to the sender for confirmation
                        send(Frame.Text(jsonResponse))
                    }
                }
            } catch (e: Exception) {
                // Optionally log errors here
            } finally {
                // Remove this session from the sender's list on disconnect
                personalSessions[senderUsername]?.remove(this)
            }
        }
    }
}
