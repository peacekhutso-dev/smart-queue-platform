import { create } from 'zustand';
import { QueueEntry, QueueBroadcast } from '../types';

interface QueueState {
  myTicket: QueueEntry | null;
  liveBroadcast: QueueBroadcast | null;
  history: QueueEntry[];
  setMyTicket: (ticket: QueueEntry | null) => void;
  setLiveBroadcast: (broadcast: QueueBroadcast) => void;
  setHistory: (history: QueueEntry[]) => void;
  clearTicket: () => void;
}

export const useQueueStore = create<QueueState>((set) => ({
  myTicket: null,
  liveBroadcast: null,
  history: [],
  setMyTicket: (ticket) => set({ myTicket: ticket }),
  setLiveBroadcast: (broadcast) => set({ liveBroadcast: broadcast }),
  setHistory: (history) => set({ history }),
  clearTicket: () => set({ myTicket: null, liveBroadcast: null }),
}));