// Configuration
const CUSTOMER_ID = "CUST-001";
let ws = null;
let conversationId = null;
let currentAgent = "router";
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;
const RECONNECT_DELAY = 2000;

// DOM Elements
const chatMessages = document.getElementById('chat-messages');
const messageInput = document.getElementById('message-input');
const sendButton = document.getElementById('send-button');
const connectionStatus = document.getElementById('connection-status');
const currentAgentBadge = document.getElementById('current-agent-badge');
const conversationIdElement = document.getElementById('conversation-id');
const handoffList = document.getElementById('handoff-list');

// Agent color mapping
const AGENT_COLORS = {
    'router': 'agent-router',
    'order': 'agent-order',
    'product': 'agent-product',
    'returns': 'agent-returns',
    'complaint': 'agent-complaint'
};

// Initialize WebSocket connection
function connectWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const basePath = new URL(document.baseURI).pathname.replace(/\/$/, '');
    const wsUrl = `${protocol}//${window.location.host}${basePath}/ws`;

    updateConnectionStatus('connecting');

    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        console.log('WebSocket connected');
        updateConnectionStatus('connected');
        reconnectAttempts = 0;
    };

    ws.onmessage = (event) => {
        handleMessage(JSON.parse(event.data));
    };

    ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        updateConnectionStatus('error');
    };

    ws.onclose = () => {
        console.log('WebSocket disconnected');
        updateConnectionStatus('disconnected');
        attemptReconnect();
    };
}

// Attempt to reconnect
function attemptReconnect() {
    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
        reconnectAttempts++;
        console.log(`Reconnecting... Attempt ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS}`);
        setTimeout(connectWebSocket, RECONNECT_DELAY);
    } else {
        console.error('Max reconnection attempts reached');
        updateConnectionStatus('failed');
    }
}

// Update connection status indicator
function updateConnectionStatus(status) {
    const statusMap = {
        'connecting': { icon: 'circle-fill text-warning', text: 'Connecting...' },
        'connected': { icon: 'circle-fill text-success', text: 'Connected' },
        'disconnected': { icon: 'circle-fill text-secondary', text: 'Disconnected' },
        'error': { icon: 'circle-fill text-danger', text: 'Connection Error' },
        'failed': { icon: 'circle-fill text-danger', text: 'Connection Failed' }
    };

    const statusInfo = statusMap[status];
    connectionStatus.innerHTML = `<i class="bi bi-${statusInfo.icon}"></i> ${statusInfo.text}`;
}

// Send message
function sendMessage() {
    const message = messageInput.value.trim();

    if (!message || !ws || ws.readyState !== WebSocket.OPEN) {
        return;
    }

    // Display user message immediately
    appendMessage('user', message);

    // Send to WebSocket
    const payload = {
        customer_id: CUSTOMER_ID,
        message: message,
        conversation_id: conversationId
    };

    ws.send(JSON.stringify(payload));

    // Clear input
    messageInput.value = '';
}

// Handle incoming WebSocket messages
function handleMessage(data) {
    console.log('Received message:', data);

    // Update conversation ID if present
    if (data.conversation_id && !conversationId) {
        conversationId = data.conversation_id;
        conversationIdElement.textContent = conversationId;
    }

    // Handle different message types
    if (data.error) {
        handleError(data);
    } else if (data.content) {
        // If response includes handoff info, show it before the message
        if (data.handoff) {
            handleHandoff(data.handoff);
        }
        handleChatMessage(data);
    }
}

// Handle chat messages
function handleChatMessage(data) {
    const agent = data.agent || currentAgent;
    const content = data.content || data.message;
    const toolCalls = data.tool_calls || [];

    appendMessage('assistant', content, agent, toolCalls);
}

// Handle handoff events
function handleHandoff(handoff) {
    const fromAgent = handoff.from_agent || currentAgent;
    const toAgent = handoff.to_agent;

    // Update current agent
    currentAgent = toAgent;
    updateCurrentAgent(toAgent);

    // Add to handoff history
    addHandoffEvent(fromAgent, toAgent);

    // Display system message
    const message = `Transferring you from ${formatAgentName(fromAgent)} to ${formatAgentName(toAgent)}...`;
    appendSystemMessage(message);
}

// Handle errors
function handleError(data) {
    const errorMessage = data.error || 'An error occurred';
    appendSystemMessage(`Error: ${errorMessage}`, true);
}

// Append message to chat
function appendMessage(role, content, agent = null, toolCalls = []) {
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${role}`;

    let messageHTML = '';

    if (role === 'assistant' && agent) {
        const agentClass = AGENT_COLORS[agent] || 'agent-router';
        messageHTML += `
            <div class="message-header">
                <span class="agent-badge ${agentClass}">${formatAgentName(agent)}</span>
            </div>
        `;
    }

    if (role === 'assistant') {
        messageHTML += `<div class="message-bubble">${renderMarkdown(content)}</div>`;
    } else {
        messageHTML += `<div class="message-bubble">${escapeHtml(content)}</div>`;
    }

    messageDiv.innerHTML = messageHTML;
    chatMessages.appendChild(messageDiv);

    // Add tool calls if present
    if (toolCalls.length > 0) {
        const toolCallsDiv = createToolCallsElement(toolCalls);
        chatMessages.appendChild(toolCallsDiv);
    }

    scrollToBottom();
}

// Append system message
function appendSystemMessage(content, isError = false) {
    const messageDiv = document.createElement('div');
    messageDiv.className = 'system-message';

    const bgClass = isError ? 'bg-danger-subtle border-danger text-danger-emphasis' : '';
    messageDiv.innerHTML = `
        <div class="system-message-content ${bgClass}">
            ${escapeHtml(content)}
        </div>
    `;

    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

// Create tool calls element
function createToolCallsElement(toolCalls) {
    const container = document.createElement('div');
    container.className = 'tool-calls';

    toolCalls.forEach((toolCall, index) => {
        const toolDiv = document.createElement('div');
        toolDiv.className = 'tool-call tool-call-collapsed';

        const toolName = toolCall.function?.name || toolCall.name || 'Unknown Tool';
        const toolArgs = toolCall.function?.arguments || toolCall.arguments || {};

        // Format arguments as JSON
        let argsJson;
        try {
            argsJson = typeof toolArgs === 'string'
                ? JSON.stringify(JSON.parse(toolArgs), null, 2)
                : JSON.stringify(toolArgs, null, 2);
        } catch (e) {
            argsJson = String(toolArgs);
        }

        toolDiv.innerHTML = `
            <div class="tool-call-header" onclick="toggleToolCall(this)">
                <span><i class="bi bi-tools"></i> ${escapeHtml(toolName)}</span>
                <i class="bi bi-chevron-down tool-call-icon"></i>
            </div>
            <div class="tool-call-content">${escapeHtml(argsJson)}</div>
        `;

        container.appendChild(toolDiv);
    });

    return container;
}

// Toggle tool call expansion
function toggleToolCall(header) {
    const toolCall = header.parentElement;
    toolCall.classList.toggle('tool-call-collapsed');
}

// Update current agent badge
function updateCurrentAgent(agent) {
    const agentClass = AGENT_COLORS[agent] || 'agent-router';
    currentAgentBadge.className = `badge ${agentClass}`;
    currentAgentBadge.textContent = formatAgentName(agent);
}

// Add handoff event to sidebar
function addHandoffEvent(fromAgent, toAgent) {
    // Clear "no handoffs" message
    if (handoffList.querySelector('.text-muted')) {
        handoffList.innerHTML = '';
    }

    const handoffDiv = document.createElement('div');
    handoffDiv.className = 'handoff-item';

    const time = new Date().toLocaleTimeString();
    handoffDiv.innerHTML = `
        <div>${formatAgentName(fromAgent)} &rarr; ${formatAgentName(toAgent)}</div>
        <div class="time">${time}</div>
    `;

    handoffList.appendChild(handoffDiv);

    // Scroll to bottom of handoff list
    handoffList.scrollTop = handoffList.scrollHeight;
}

// Format agent name for display
function formatAgentName(agent) {
    const names = {
        'router': 'Router',
        'order': 'Order Specialist',
        'product': 'Product Specialist',
        'returns': 'Returns Specialist',
        'complaint': 'Manager'
    };
    return names[agent] || agent;
}

// Scroll chat to bottom
function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Render markdown to safe HTML
function renderMarkdown(text) {
    if (!text) return '';
    const html = marked.parse(text);
    return DOMPurify.sanitize(html, {
        ADD_TAGS: ['img'],
        ADD_ATTR: ['src', 'alt', 'title', 'width', 'height', 'loading']
    });
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Event Listeners
sendButton.addEventListener('click', sendMessage);

messageInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        sendMessage();
    }
});

// Example prompts
document.querySelectorAll('.example-prompt').forEach(button => {
    button.addEventListener('click', () => {
        messageInput.value = button.dataset.prompt;
        messageInput.focus();
    });
});

// Initialize connection on page load
connectWebSocket();
