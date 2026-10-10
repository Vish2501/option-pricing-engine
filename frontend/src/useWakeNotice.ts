import { useCallback, useState } from "react";

const WAKE_NOTICE_DELAY_MS = 4000;

export type TrackRequest = <T>(request: Promise<T>) => Promise<T>;

// The API runs on free hosting that sleeps when idle, so a slow request usually means a
// cold start. Wrapping a request with `track` raises a flag if it is still pending after a
// few seconds, so the page can explain the wait instead of just showing a spinner.
export function useWakeNotice(): [boolean, TrackRequest] {
  const [waking, setWaking] = useState(false);

  const track = useCallback(async <T,>(request: Promise<T>): Promise<T> => {
    const timer = window.setTimeout(() => setWaking(true), WAKE_NOTICE_DELAY_MS);
    try {
      return await request;
    } finally {
      window.clearTimeout(timer);
      setWaking(false);
    }
  }, []);

  return [waking, track];
}
