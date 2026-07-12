import { useState, useRef, useEffect } from 'react'
import { Send, Paperclip, Mic, Volume2, Type, History, Plus, X, MessageSquare } from 'lucide-react'
import ScreenHeader from '@/components/layout/ScreenHeader'
import { assistantService } from '@/api/Api'
import { ChatMessage } from '@/types'

const QUICK_REPLIES = ['Check ID Status', 'Passport Fees', 'Nearby Branches', 'Queue Status']

const INITIAL_MESSAGE: ChatMessage = {
  id: '0',
  role: 'assistant',
  content: "Molo! I'm your DHA Smart Assistant. How can I help you today? You can ask about ID applications, passports, or check your queue status.",
  timestamp: new Date().toISOString(),
}

interface ChatSession {
  id: string
  title: string
  messages: ChatMessage[]
  timestamp: string
}

const STORAGE_KEY = 'dha_chat_sessions'

function loadSessions(): ChatSession[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
  } catch {
    return []
  }
}

function saveSessions(sessions: ChatSession[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions))
}

export default function AssistantPage() {
  const [messages, setMessages]     = useState<ChatMessage[]>([INITIAL_MESSAGE])
  const [input, setInput]           = useState('')
  const [loading, setLoading]       = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [sessions, setSessions]     = useState<ChatSession[]>(loadSessions)
  const bottomRef                   = useRef<HTMLDivElement>(null)
  const historyRef                  = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // Close history modal when clicking outside
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (historyRef.current && !historyRef.current.contains(e.target as Node)) {
        setShowHistory(false)
      }
    }
    if (showHistory) document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [showHistory])

  const persistSession = (msgs: ChatMessage[]) => {
    const userMsg = msgs.find((m) => m.role === 'user')
    if (!userMsg) return
    const session: ChatSession = {
      id: Date.now().toString(),
      title: userMsg.content.length > 45 ? userMsg.content.slice(0, 45) + '…' : userMsg.content,
      messages: msgs,
      timestamp: new Date().toISOString(),
    }
    const updated = [session, ...sessions].slice(0, 15)
    setSessions(updated)
    saveSessions(updated)
  }

  const startNewChat = () => {
    persistSession(messages)
    setMessages([{ ...INITIAL_MESSAGE, id: Date.now().toString(), timestamp: new Date().toISOString() }])
    setInput('')
    setShowHistory(false)
  }

  const loadSession = (session: ChatSession) => {
    persistSession(messages)
    setMessages(session.messages)
    setShowHistory(false)
  }

  const deleteSession = (id: string, e: React.MouseEvent) => {
    e.stopPropagation()
    const updated = sessions.filter((s) => s.id !== id)
    setSessions(updated)
    saveSessions(updated)
  }

  const send = async (text: string) => {
    if (!text.trim() || loading) return
    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: text,
      timestamp: new Date().toISOString(),
    }
    setMessages((m) => [...m, userMsg])
    setInput('')
    setLoading(true)

    try {
      const res = await assistantService.sendMessage(text, messages)
      const assistantMsg: ChatMessage = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: res.data.data.reply,
        timestamp: new Date().toISOString(),
      }
      setMessages((m) => [...m, assistantMsg])
    } catch {
      setMessages((m) => [...m, {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: "I'm sorry, I'm having trouble connecting right now. Please try again shortly.",
        timestamp: new Date().toISOString(),
      }])
    } finally {
      setLoading(false)
    }
  }

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleTimeString('en-ZA', { hour: '2-digit', minute: '2-digit' })

  const formatDate = (iso: string) => {
    const d = new Date(iso)
    const today = new Date()
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)
    if (d.toDateString() === today.toDateString()) return 'Today'
    if (d.toDateString() === yesterday.toDateString()) return 'Yesterday'
    return d.toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' })
  }

  const historyButton = (
    <div className="relative" ref={historyRef}>
      <button
        onClick={() => setShowHistory((v) => !v)}
        className="p-1.5 rounded-full hover:bg-gray-100 transition-colors relative"
        aria-label="Chat history"
      >
        <History className="w-5 h-5 text-gray-500" />
        {sessions.length > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-2 h-2 bg-primary-500 rounded-full" />
        )}
      </button>

      {showHistory && (
        <div className="absolute right-0 top-10 w-72 bg-white rounded-2xl shadow-2xl border border-gray-200 z-50 overflow-hidden">
          {/* Modal header */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
            <span className="text-sm font-bold text-gray-800">Recent Chats</span>
            <button
              onClick={startNewChat}
              className="flex items-center gap-1 text-xs font-semibold text-primary-500 hover:text-primary-600 bg-primary-50 px-2.5 py-1 rounded-full transition-colors"
            >
              <Plus className="w-3 h-3" /> New Chat
            </button>
          </div>

          {/* Session list */}
          {sessions.length === 0 ? (
            <div className="flex flex-col items-center gap-2 py-8 px-4 text-center">
              <MessageSquare className="w-8 h-8 text-gray-200" />
              <p className="text-sm text-gray-400">No previous chats yet</p>
            </div>
          ) : (
            <div className="flex flex-col max-h-64 overflow-y-auto divide-y divide-gray-100">
              {sessions.map((s) => (
                <button
                  key={s.id}
                  onClick={() => loadSession(s)}
                  className="flex items-start gap-3 px-4 py-3 text-left hover:bg-gray-50 transition-colors group"
                >
                  <div className="w-7 h-7 rounded-full bg-gray-100 flex items-center justify-center shrink-0 mt-0.5">
                    <MessageSquare className="w-3.5 h-3.5 text-gray-400" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-800 truncate">{s.title}</p>
                    <p className="text-xs text-gray-400 mt-0.5">{formatDate(s.timestamp)}</p>
                  </div>
                  <button
                    onClick={(e) => deleteSession(s.id, e)}
                    className="opacity-0 group-hover:opacity-100 p-1 rounded-lg hover:bg-red-50 transition-all shrink-0"
                  >
                    <X className="w-3.5 h-3.5 text-red-400" />
                  </button>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )

  return (
    <div className="flex flex-col h-screen bg-gray-50 overscroll-none">
      <ScreenHeader title="DHA Virtual Assistant" showSettings={false} rightElement={historyButton} />

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4">
        <div className="flex justify-center">
          <span className="text-xs bg-white border border-gray-200 text-gray-400 px-3 py-1 rounded-full">
            Today, {new Date().toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' })}
          </span>
        </div>

        {messages.map((msg) => (
          <div key={msg.id} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start gap-2'}`}>
            {msg.role === 'assistant' && (
              <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center shrink-0 mt-auto">
                🤖
              </div>
            )}
            <div className="max-w-[78%]">
              <div className={`
                px-4 py-3 rounded-2xl text-sm leading-relaxed
                ${msg.role === 'user'
                  ? 'bg-primary-500 text-white rounded-br-sm'
                  : 'bg-white text-gray-800 shadow-sm rounded-bl-sm'
                }
              `}>
                {msg.content}
              </div>
              <p className={`text-xs text-gray-400 mt-1 ${msg.role === 'user' ? 'text-right' : ''}`}>
                {formatTime(msg.timestamp)}
                {msg.role === 'user' && ' ⊙'}
              </p>

              {msg.role === 'assistant' && msg.content.toLowerCase().includes('checklist') && (
                <div className="mt-2 border border-gray-200 rounded-2xl overflow-hidden bg-white">
                  <div className="flex items-center gap-2 px-4 py-3 bg-primary-50 border-b border-gray-100">
                    <span className="text-primary-500">📋</span>
                    <span className="font-bold text-gray-800 text-sm">Document Checklist</span>
                  </div>
                  <div className="p-4 flex flex-col gap-2">
                    {[
                      { label: 'Certified Copy of Birth Certificate', done: true },
                      { label: 'Proof of Residential Address', done: false },
                      { label: 'Parental Consent Form (for minors)', done: false },
                    ].map(({ label, done }) => (
                      <div key={label} className="flex items-center gap-2">
                        <div className={`w-4 h-4 rounded border-2 flex items-center justify-center shrink-0 ${done ? 'bg-primary-500 border-primary-500' : 'border-gray-300'}`}>
                          {done && <span className="text-white text-xs">✓</span>}
                        </div>
                        <span className="text-sm text-gray-700">{label}</span>
                      </div>
                    ))}
                    <button className="mt-2 w-full bg-primary-500 text-white text-sm font-bold py-2.5 rounded-xl flex items-center justify-center gap-2">
                      Upload Missing Documents →
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        ))}

        {loading && (
          <div className="flex gap-2 items-end">
            <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center shrink-0">🤖</div>
            <div className="bg-white rounded-2xl rounded-bl-sm px-4 py-3 shadow-sm">
              <div className="flex gap-1">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="w-2 h-2 bg-gray-300 rounded-full animate-bounce" style={{ animationDelay: `${i * 150}ms` }} />
                ))}
              </div>
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Quick replies */}
      <div className="px-4 pb-2 flex gap-2 overflow-x-auto">
        {QUICK_REPLIES.map((q) => (
          <button
            key={q}
            onClick={() => send(q)}
            className="shrink-0 border border-gray-200 bg-white text-sm text-gray-700 px-3 py-1.5 rounded-full whitespace-nowrap hover:bg-gray-50"
          >
            {q}
          </button>
        ))}
      </div>

      {/* Input bar */}
      <div className="px-4 pb-4 pt-2 bg-white border-t border-gray-100">
        <div className="flex items-center gap-2 mb-2 text-gray-400">
          <Volume2 className="w-4 h-4" />
          <Type className="w-4 h-4" />
          <div className="flex-1" />
          <Paperclip className="w-4 h-4" />
          <Mic className="w-4 h-4" />
        </div>
        <div className="flex items-center gap-2">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && send(input)}
            placeholder="Type your message..."
            className="flex-1 border border-gray-200 rounded-2xl px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          <button
            onClick={() => send(input)}
            disabled={!input.trim() || loading}
            className="w-10 h-10 bg-primary-500 rounded-full flex items-center justify-center disabled:opacity-40"
          >
            <Send className="w-4 h-4 text-white" />
          </button>
        </div>
      </div>
    </div>
  )
}
