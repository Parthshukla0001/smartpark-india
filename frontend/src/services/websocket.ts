import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { useEffect, useRef, useState, useCallback } from 'react';
import { ParkingEventPayload } from '../types';

function getWsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws`;
}

class WebSocketManager {
  private client: Client | null = null;
  private connected = false;
  private subscribers: Map<string, Set<(payload: ParkingEventPayload) => void>> = new Map();
  private activeSubscriptions: Map<string, StompSubscription> = new Map();
  private connectionListeners: Set<(connected: boolean) => void> = new Set();

  public isConnected(): boolean {
    return this.connected;
  }

  public onConnectionChange(listener: (connected: boolean) => void): () => void {
    this.connectionListeners.add(listener);
    listener(this.connected);
    return () => {
      this.connectionListeners.delete(listener);
    };
  }

  public connect(token: string) {
    if (this.client && this.connected) {
      return;
    }

    if (this.client) {
      this.client.deactivate();
    }

    const brokerUrl = getWsUrl();

    this.client = new Client({
      brokerURL: brokerUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: (msg: string) => {
        // debug logging can be silenced in production
      },
      onConnect: () => {
        this.connected = true;
        this.notifyConnectionState(true);
        // Resubscribe to any pending topics
        this.subscribers.forEach((_, topic) => {
          this.subscribeToTopic(topic);
        });
      },
      onDisconnect: () => {
        this.connected = false;
        this.activeSubscriptions.clear();
        this.notifyConnectionState(false);
      },
      onStompError: (frame) => {
        console.warn('STOMP error:', frame.headers['message'], frame.body);
      },
      onWebSocketError: (event) => {
        console.warn('WebSocket error:', event);
      },
    });

    this.client.activate();
  }

  public disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.connected = false;
      this.activeSubscriptions.clear();
      this.notifyConnectionState(false);
    }
  }

  private notifyConnectionState(state: boolean) {
    this.connectionListeners.forEach((listener) => listener(state));
  }

  private subscribeToTopic(topic: string) {
    if (!this.client || !this.connected) return;
    if (this.activeSubscriptions.has(topic)) return;

    try {
      const sub = this.client.subscribe(topic, (message: IMessage) => {
        try {
          const payload: ParkingEventPayload = JSON.parse(message.body);
          const callbacks = this.subscribers.get(topic);
          if (callbacks) {
            callbacks.forEach((cb) => cb(payload));
          }
        } catch (e) {
          console.error('Failed to parse STOMP message body:', e);
        }
      });
      this.activeSubscriptions.set(topic, sub);
    } catch (e) {
      console.error('Error subscribing to topic:', topic, e);
    }
  }

  public subscribe(lotId: number, callback: (payload: ParkingEventPayload) => void): () => void {
    const topic = `/topic/parking-lots/${lotId}`;

    if (!this.subscribers.has(topic)) {
      this.subscribers.set(topic, new Set());
    }
    this.subscribers.get(topic)!.add(callback);

    if (this.connected) {
      this.subscribeToTopic(topic);
    }

    return () => {
      const callbacks = this.subscribers.get(topic);
      if (callbacks) {
        callbacks.delete(callback);
        if (callbacks.size === 0) {
          this.subscribers.delete(topic);
          const sub = this.activeSubscriptions.get(topic);
          if (sub) {
            sub.unsubscribe();
            this.activeSubscriptions.delete(topic);
          }
        }
      }
    };
  }
}

export const wsManager = new WebSocketManager();

export function useParkingLotLiveUpdates(
  lotId?: number,
  onEvent?: (payload: ParkingEventPayload) => void
) {
  const [isConnected, setIsConnected] = useState(wsManager.isConnected());
  const [lastEvent, setLastEvent] = useState<ParkingEventPayload | null>(null);
  const callbackRef = useRef(onEvent);
  callbackRef.current = onEvent;

  useEffect(() => {
    const unsubConnection = wsManager.onConnectionChange(setIsConnected);
    return unsubConnection;
  }, []);

  useEffect(() => {
    if (!lotId) return;

    const handlePayload = (payload: ParkingEventPayload) => {
      setLastEvent(payload);
      if (callbackRef.current) {
        callbackRef.current(payload);
      }
    };

    const unsubscribe = wsManager.subscribe(lotId, handlePayload);
    return () => {
      unsubscribe();
    };
  }, [lotId]);

  return { isConnected, lastEvent };
}
