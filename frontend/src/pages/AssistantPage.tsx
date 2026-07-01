import { useState, useRef, useEffect } from 'react'
import { Send, Paperclip, Mic, Volume2, Type } from 'lucide-react'
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

export default function AssistantPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([INITIAL_MESSAGE])
  const [input, setInput]       = useState('')
  const [loading, setLoading]   = useState(false)
  const bottomRef               = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

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

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      <ScreenHeader title="DHA Virtual Assistant" />

      {/* Messages */}
      <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4">
        {/* Date label */}
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
            <div className={`max-w-[78%] ${msg.role === 'user' ? '' : ''}`}>
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

              {/* Document checklist card — shown for relevant assistant messages */}
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