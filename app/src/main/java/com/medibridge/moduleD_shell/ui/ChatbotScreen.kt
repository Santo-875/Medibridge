package com.medibridge.moduleD_shell.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medibridge.core.chat.ChatRepository
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ChatbotScreen — "Medi" AI assistant UI shell.
 *
 * EXTENSION POINTS FOR MODULE D (AI Chat):
 *   • Replace sendDummyReply() with a Retrofit call to the AI backend.
 *   • Wire [ChatViewModel] (to be created) to hold message history in StateFlow.
 *   • Deep-link medication cards from chat responses to medication detail screens.
 *
 * TODO: Module D wires AI chat API (Retrofit) here.
 * TODO: Module D/A — parse intents from user messages to trigger prescription scans.
 *
 * STUB / NOTE:
 * Medi reads from shared MedicationObject history — populated by Module 1 (voice sessions),
 * read by Module 2 (safety) and Module 3 (schedule/summary), always reflects latest state.
 * No new UI needed, just wire data source later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val chatRepository = remember { ChatRepository(context) }
    var messageText by remember { mutableStateOf("") }
    val messages    = remember { mutableStateListOf(
        ChatMessage(id = "init", text = "Hi! I'm Medi 👋 — your personal medication assistant.\n\nAsk me about your medicines, side effects, schedule, or drug interactions.", isBot = true)
    ) }
    val listState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Filled.SmartToy,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text       = "Medi",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                text  = "AI Medication Assistant",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = {
                    var isSummarizing by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = {
                            if (!isSummarizing) {
                                isSummarizing = true
                                scope.launch {
                                    val historyLines = messages.map { "${if (it.isBot) "Medi" else "Patient"}: ${it.text}" }
                                    val summaryText = chatRepository.summarizeSession(historyLines)
                                    messages.add(
                                        ChatMessage(
                                            id = UUID.randomUUID().toString(),
                                            text = summaryText,
                                            isBot = true
                                        )
                                    )
                                    isSummarizing = false
                                }
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Summarize",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Summarize",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Message list ─────────────────────────────────────────────────
            LazyColumn(
                state          = listState,
                modifier       = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }
            }

            // ── Suggestion chips ─────────────────────────────────────────────
            QuickSuggestions { suggestion ->
                messageText = suggestion
            }

            HorizontalDivider()

            // ── Input bar ────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value          = messageText,
                    onValueChange  = { messageText = it },
                    placeholder    = { Text("Ask about your medications…") },
                    modifier       = Modifier.weight(1f),
                    shape          = RoundedCornerShape(24.dp),
                    singleLine     = true,
                    colors         = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        val trimmed = messageText.trim()
                        if (trimmed.isNotEmpty()) {
                            messages.add(ChatMessage(id = UUID.randomUUID().toString(), text = trimmed, isBot = false))
                            messageText = ""

                            scope.launch {
                                val reply = chatRepository.sendMessage(trimmed)
                                messages.add(
                                    ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        text = reply,
                                        isBot = true
                                    )
                                )
                            }
                        }
                    },
                    modifier       = Modifier.size(52.dp),
                    shape          = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint               = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat bubble
// ─────────────────────────────────────────────────────────────────────────────

data class ChatMessage(val id: String, val text: String, val isBot: Boolean)

@Composable
private fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.isBot)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.primary

    val textColor = if (message.isBot)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.onPrimary

    val alignment = if (message.isBot) Alignment.Start else Alignment.End
    val shape     = if (message.isBot)
        RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
    else
        RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (message.isBot) {
            Row(verticalAlignment = Alignment.Bottom) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Surface(shape = shape, color = bubbleColor) {
                    Text(
                        text     = message.text,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = textColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        } else {
            Surface(
                shape = shape,
                color = bubbleColor,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text     = message.text,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = textColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick suggestion chips
// ─────────────────────────────────────────────────────────────────────────────

private val suggestions = listOf(
    "My medications today",
    "Any drug interactions?",
    "Side effects of Metformin",
    "When to take Amlodipine?"
)

@Composable
private fun QuickSuggestions(onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionChip(
                onClick = { onSelect(suggestion) },
                label   = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dummy bot reply — replace with Retrofit AI call in Module D
// ─────────────────────────────────────────────────────────────────────────────
private fun dummyBotReply(userMessage: String): String {
    // TODO: Module D — replace this function body with an API call:
    //   return chatApiService.chat(ChatRequest(message = userMessage)).reply
    return when {
        userMessage.contains("metformin", ignoreCase = true) ->
            "Metformin (500mg) should be taken twice daily with meals. Common side effects include nausea and diarrhea — these usually subside after a few weeks."
        userMessage.contains("interaction", ignoreCase = true) ->
            "⚠️ I detected a potential interaction between Amlodipine and Lisinopril. Both lower blood pressure — combining them may cause dizziness. Please consult your doctor."
        userMessage.contains("schedule", ignoreCase = true) || userMessage.contains("today", ignoreCase = true) ->
            "Today's schedule:\n• 8:00 AM — Metformin 500mg (with breakfast)\n• 9:00 AM — Amlodipine 5mg + Vitamin D3\n• 10:00 PM — Lisinopril 10mg (before bed)"
        else ->
            "I'm Medi, your AI medication assistant! I can help with your medication schedule, side effects, drug interactions, and dosage queries. What would you like to know?"
    }
}
