"use client";

import React, { Suspense, useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Client } from "@stomp/stompjs";
import {
  ArrowLeftRight,
  Check,
  CheckCircle2,
  MapPin,
  Send,
  Star,
  X,
} from "lucide-react";
import AppShell from "@/components/AppShell";
import { api, CONDITION_LABELS, getStoredTokens, WS_BASE_URL } from "@/lib/api";
import {
  ChatMessage,
  ChatThread,
  HaveListing,
  MeetupSpot,
} from "@/lib/types";

function ChatContent() {
  const searchParams = useSearchParams();
  const initialThreadId = searchParams.get("threadId");

  const [threads, setThreads] = useState<ChatThread[]>([]);
  const [activeThread, setActiveThread] = useState<ChatThread | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputBody, setInputBody] = useState("");
  const [myUserId, setMyUserId] = useState<string | null>(null);

  // Propose Swap Modal state
  const [proposeOpen, setProposeOpen] = useState(false);
  const [myHaveListings, setMyHaveListings] = useState<HaveListing[]>([]);
  const [theirHaveListings, setTheirHaveListings] = useState<HaveListing[]>([]);
  const [meetupSpots, setMeetupSpots] = useState<MeetupSpot[]>([]);
  const [selectedMyListingId, setSelectedMyListingId] = useState("");
  const [selectedTheirListingId, setSelectedTheirListingId] = useState("");
  const [selectedMeetupSpotId, setSelectedMeetupSpotId] = useState("");
  const [proposalNote, setProposalNote] = useState("");

  // Meetup Spot Quick Picker
  const [meetupPickerOpen, setMeetupPickerOpen] = useState(false);

  // Rating Modal state
  const [ratingSwapId, setRatingSwapId] = useState<string | null>(null);
  const [ratingStars, setRatingStars] = useState(5);
  const [ratingComment, setRatingComment] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const stompRef = useRef<Client | null>(null);

  useEffect(() => {
    const { userId } = getStoredTokens();
    setMyUserId(userId);
    api
      .getMeetupSpots()
      .then((res) => setMeetupSpots(res.items))
      .catch(() => {});
  }, []);

  const loadThreads = useCallback(async () => {
    try {
      const res = await api.getChatThreads();
      setThreads(res.items);
      if (res.items.length > 0) {
        const match = initialThreadId
          ? res.items.find((t) => t.id === initialThreadId)
          : null;
        setActiveThread((prev) => match || prev || res.items[0]);
      }
    } catch {
      // ignore
    }
  }, [initialThreadId]);

  useEffect(() => {
    loadThreads();
  }, [loadThreads]);

  const loadMessages = useCallback(async (threadId: string) => {
    try {
      const res = await api.getChatMessages(threadId);
      // Reverse chronological cursor -> display chronological top-to-bottom
      setMessages([...res.items].reverse());
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    if (!activeThread) return;
    loadMessages(activeThread.id);

    const { accessToken } = getStoredTokens();
    const client = new Client({
      brokerURL: WS_BASE_URL,
      connectHeaders: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/threads.${activeThread.id}`, () => {
          loadMessages(activeThread.id);
        });
      },
    });

    try {
      client.activate();
      stompRef.current = client;
    } catch {
      // Fallback to REST updates
    }

    return () => {
      if (stompRef.current) {
        stompRef.current.deactivate();
        stompRef.current = null;
      }
    };
  }, [activeThread, loadMessages]);

  const handleSendText = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeThread || !inputBody.trim()) return;
    setActionError(null);
    try {
      await api.sendChatMessage(activeThread.id, inputBody);
      setInputBody("");
      loadMessages(activeThread.id);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to send message");
    }
  };

  const handleSendMeetupSpot = async (spot: MeetupSpot) => {
    if (!activeThread) return;
    try {
      await api.sendChatMessage(
        activeThread.id,
        `Let's meet at ${spot.name} (${spot.addressHint}) for the swap!`,
        spot.id
      );
      setMeetupPickerOpen(false);
      loadMessages(activeThread.id);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to send meetup spot");
    }
  };

  const openProposeSwapModal = async () => {
    if (!activeThread) return;
    setActionError(null);
    setProposeOpen(true);
    try {
      const [myRes, theirRes] = await Promise.all([
        api.getHaveListings(),
        api.getHaveListings(activeThread.otherUserId),
      ]);
      const myActive = myRes.items.filter((i) => i.status === "ACTIVE");
      const theirActive = theirRes.items.filter((i) => i.status === "ACTIVE");
      setMyHaveListings(myActive);
      setTheirHaveListings(theirActive);
      if (myActive.length > 0) setSelectedMyListingId(myActive[0].id);
      if (theirActive.length > 0) setSelectedTheirListingId(theirActive[0].id);
      if (meetupSpots.length > 0) setSelectedMeetupSpotId(meetupSpots[0].id);
    } catch {
      // ignore
    }
  };

  const handleSubmitSwapProposal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeThread || !selectedMyListingId || !selectedTheirListingId) return;
    setActionError(null);
    try {
      await api.proposeSwap({
        threadId: activeThread.id,
        myHaveListingId: selectedMyListingId,
        theirHaveListingId: selectedTheirListingId,
        meetupSpotId: selectedMeetupSpotId || undefined,
        message: proposalNote || undefined,
      });
      setProposeOpen(false);
      setProposalNote("");
      loadMessages(activeThread.id);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Could not propose swap");
    }
  };

  const handleSwapAction = async (
    swapId: string,
    action: "ACCEPT" | "DECLINE" | "CANCEL" | "CONFIRM"
  ) => {
    if (!activeThread) return;
    setActionError(null);
    try {
      if (action === "ACCEPT") await api.acceptSwap(swapId);
      if (action === "DECLINE") await api.declineSwap(swapId);
      if (action === "CANCEL") await api.cancelSwap(swapId);
      if (action === "CONFIRM") await api.confirmSwap(swapId);
      loadMessages(activeThread.id);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Swap action failed");
    }
  };

  const handleSubmitRating = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ratingSwapId || !activeThread) return;
    try {
      await api.submitRating(ratingSwapId, ratingStars, ratingComment);
      setRatingSwapId(null);
      setRatingComment("");
      loadMessages(activeThread.id);
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Rating failed");
    }
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
      {/* Threads Sidebar */}
      <div className="md:col-span-4 bg-slate-900 border border-slate-800 rounded-2xl p-3 flex flex-col max-h-[75vh]">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 px-2 pb-2 border-b border-slate-800">
          Active Swap Chats ({threads.length})
        </h2>
        <div className="flex-1 overflow-y-auto divide-y divide-slate-800/70 mt-1">
          {threads.length === 0 ? (
            <p className="text-xs text-slate-500 p-4 text-center">
              No chats yet. Pick a match and tap &ldquo;Chat &amp; Swap&rdquo;!
            </p>
          ) : (
            threads.map((t) => {
              const selected = activeThread?.id === t.id;
              return (
                <button
                  key={t.id}
                  onClick={() => setActiveThread(t)}
                  data-testid={`thread-item-${t.otherUserDisplayName}`}
                  className={`w-full text-left p-2.5 rounded-xl transition flex items-start justify-between gap-2 ${
                    selected ? "bg-indigo-600/20 text-white" : "hover:bg-slate-800/60"
                  }`}
                >
                  <div className="min-w-0">
                    <div className="font-bold text-xs text-white truncate">
                      @{t.otherUserDisplayName}
                    </div>
                    <div className="text-[11px] text-emerald-400 flex items-center gap-1">
                      <MapPin className="w-3 h-3" />
                      {t.otherUserLocalityName}
                    </div>
                    <p className="text-[11px] text-slate-400 truncate mt-0.5">
                      {t.lastMessagePreview}
                    </p>
                  </div>
                  <span className="text-[10px] text-amber-400 flex items-center gap-0.5 shrink-0">
                    <Star className="w-3 h-3 fill-amber-400" />
                    {Number(t.otherUserRatingAvg).toFixed(1)}
                  </span>
                </button>
              );
            })
          )}
        </div>
      </div>

      {/* Active Conversation Panel */}
      <div className="md:col-span-8 bg-slate-900 border border-slate-800 rounded-2xl flex flex-col h-[72vh]">
        {activeThread ? (
          <>
            {/* Thread Header */}
            <div className="p-3.5 border-b border-slate-800 flex flex-wrap items-center justify-between gap-2">
              <div>
                <Link
                  href={`/users/${activeThread.otherUserId}`}
                  className="font-bold text-sm text-white hover:text-indigo-300"
                >
                  @{activeThread.otherUserDisplayName}
                </Link>
                <div className="text-xs text-slate-400 flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 text-emerald-400" />
                  <span>{activeThread.otherUserLocalityName}</span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => setMeetupPickerOpen(!meetupPickerOpen)}
                  data-testid="pick-meetup-btn"
                  className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-1 border border-slate-700"
                >
                  <MapPin className="w-3.5 h-3.5 text-emerald-400" />
                  Meetup Spot
                </button>
                <button
                  onClick={openProposeSwapModal}
                  data-testid="open-propose-swap-btn"
                  className="px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow-md shadow-indigo-600/20"
                >
                  <ArrowLeftRight className="w-3.5 h-3.5" />
                  Propose Swap
                </button>
              </div>
            </div>

            {actionError && (
              <div className="mx-3 mt-2 p-2.5 rounded-xl bg-rose-950/70 border border-rose-800 text-rose-200 text-xs">
                {actionError}
              </div>
            )}

            {/* Meetup Spot Quick Drawer */}
            {meetupPickerOpen && (
              <div className="p-3 bg-slate-950 border-b border-slate-800 max-h-44 overflow-y-auto">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold text-emerald-400">
                    Pick a Public Bangalore Meetup Spot (Malls, Gaming Cafes, Metro)
                  </span>
                  <button
                    onClick={() => setMeetupPickerOpen(false)}
                    className="text-xs text-slate-400"
                  >
                    Close
                  </button>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                  {meetupSpots.map((spot) => (
                    <button
                      key={spot.id}
                      onClick={() => handleSendMeetupSpot(spot)}
                      className="text-left p-2 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs"
                    >
                      <div className="font-semibold text-white truncate">
                        {spot.name}
                      </div>
                      <div className="text-[10px] text-slate-400 truncate">
                        {spot.localityName} • {spot.addressHint}
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Messages Stream */}
            <div
              className="flex-1 overflow-y-auto p-3.5 space-y-3"
              data-testid="chat-messages-list"
            >
              {messages.map((msg) => {
                const isMe = msg.senderId === myUserId;
                const swap = msg.swap;

                return (
                  <div
                    key={msg.id}
                    className={`flex flex-col ${
                      msg.messageType === "SYSTEM"
                        ? "items-center"
                        : isMe
                        ? "items-end"
                        : "items-start"
                    }`}
                  >
                    {msg.messageType === "SYSTEM" ? (
                      <div className="px-3 py-1.5 rounded-full bg-slate-800/90 border border-slate-700 text-[11px] text-slate-300 text-center max-w-md">
                        {msg.body}
                      </div>
                    ) : (
                      <div
                        className={`max-w-md rounded-2xl p-3 text-xs ${
                          isMe
                            ? "bg-indigo-600/25 border border-indigo-500/40 text-slate-100"
                            : "bg-slate-800/90 border border-slate-700 text-slate-100"
                        }`}
                      >
                        <div className="text-[10px] text-slate-400 mb-1 font-medium">
                          @{msg.senderDisplayName}
                        </div>
                        <p className="leading-relaxed">{msg.body}</p>

                        {msg.meetupSpotName && !swap && (
                          <div className="mt-2 p-2 rounded-xl bg-emerald-950/50 border border-emerald-700/50 text-emerald-200 flex items-start gap-1.5">
                            <MapPin className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                            <div>
                              <div className="font-semibold">{msg.meetupSpotName}</div>
                              <div className="text-[10px] opacity-80">
                                {msg.meetupSpotAddress}
                              </div>
                            </div>
                          </div>
                        )}

                        {/* Interactive Swap Proposal Card */}
                        {swap && (
                          <div
                            data-testid={`swap-card-${swap.id}`}
                            className="mt-2.5 p-3 rounded-xl bg-slate-950/90 border border-indigo-500/40 space-y-2.5"
                          >
                            <div className="flex items-center justify-between">
                              <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-400 flex items-center gap-1">
                                <ArrowLeftRight className="w-3 h-3" /> Swap Proposal
                              </span>
                              <span
                                data-testid={`swap-status-${swap.status}`}
                                className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                                  swap.status === "COMPLETED"
                                    ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/40"
                                    : swap.status === "ACCEPTED"
                                    ? "bg-amber-500/20 text-amber-300 border border-amber-500/40"
                                    : swap.status === "PROPOSED"
                                    ? "bg-indigo-500/20 text-indigo-300 border border-indigo-500/40"
                                    : "bg-rose-500/20 text-rose-300"
                                }`}
                              >
                                {swap.status}
                              </span>
                            </div>

                            <div className="grid grid-cols-2 gap-2 text-[11px]">
                              <div className="p-2 rounded-lg bg-slate-900 border border-slate-800">
                                <div className="text-[10px] text-slate-400">
                                  @{swap.proposerDisplayName} offers:
                                </div>
                                <div className="font-bold text-white truncate">
                                  {swap.proposerListing.gameTitle}
                                </div>
                                <div className="text-[10px] text-indigo-300">
                                  {swap.proposerListing.platform} •{" "}
                                  {CONDITION_LABELS[swap.proposerListing.condition]}
                                </div>
                              </div>

                              <div className="p-2 rounded-lg bg-slate-900 border border-slate-800">
                                <div className="text-[10px] text-slate-400">
                                  @{swap.recipientDisplayName} offers:
                                </div>
                                <div className="font-bold text-white truncate">
                                  {swap.recipientListing.gameTitle}
                                </div>
                                <div className="text-[10px] text-emerald-300">
                                  {swap.recipientListing.platform} •{" "}
                                  {CONDITION_LABELS[swap.recipientListing.condition]}
                                </div>
                              </div>
                            </div>

                            {swap.meetupSpotName && (
                              <div className="text-[11px] text-slate-300 flex items-center gap-1">
                                <MapPin className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                                <span>Meetup: {swap.meetupSpotName}</span>
                              </div>
                            )}

                            {/* Lifecycle Action Buttons */}
                            {swap.status === "PROPOSED" && (
                              <div className="flex gap-2 pt-1">
                                {swap.recipientId === myUserId ? (
                                  <>
                                    <button
                                      onClick={() => handleSwapAction(swap.id, "ACCEPT")}
                                      data-testid="accept-swap-btn"
                                      className="flex-1 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold flex items-center justify-center gap-1"
                                    >
                                      <Check className="w-3.5 h-3.5" /> Accept Swap
                                    </button>
                                    <button
                                      onClick={() => handleSwapAction(swap.id, "DECLINE")}
                                      data-testid="decline-swap-btn"
                                      className="flex-1 py-1.5 rounded-lg bg-rose-950 hover:bg-rose-900 text-rose-200 font-semibold border border-rose-800"
                                    >
                                      Decline
                                    </button>
                                  </>
                                ) : (
                                  <button
                                    onClick={() => handleSwapAction(swap.id, "CANCEL")}
                                    className="w-full py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold"
                                  >
                                    Cancel Proposal
                                  </button>
                                )}
                              </div>
                            )}

                            {swap.status === "ACCEPTED" && (
                              <div className="space-y-2 pt-1">
                                <div className="flex items-center justify-between text-[10px] text-slate-400">
                                  <span>
                                    @{swap.proposerDisplayName}:{" "}
                                    {swap.proposerConfirmed ? "✓ Confirmed" : "Pending"}
                                  </span>
                                  <span>
                                    @{swap.recipientDisplayName}:{" "}
                                    {swap.recipientConfirmed ? "✓ Confirmed" : "Pending"}
                                  </span>
                                </div>
                                <div className="flex gap-2">
                                  <button
                                    onClick={() => handleSwapAction(swap.id, "CONFIRM")}
                                    data-testid="confirm-swap-handoff-btn"
                                    className="flex-1 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-semibold flex items-center justify-center gap-1"
                                  >
                                    <CheckCircle2 className="w-3.5 h-3.5" /> Confirm In-Person Handoff
                                  </button>
                                  <button
                                    onClick={() => handleSwapAction(swap.id, "CANCEL")}
                                    className="px-3 py-2 rounded-lg bg-slate-800 text-slate-300"
                                  >
                                    Cancel
                                  </button>
                                </div>
                              </div>
                            )}

                            {swap.status === "COMPLETED" && (
                              <div className="pt-1 flex items-center justify-between gap-2">
                                <span className="text-[11px] text-emerald-400 font-semibold">
                                  ✓ Swap Completed &amp; HAVE Lists Updated!
                                </span>
                                {!swap.myRatingSubmitted && (
                                  <button
                                    onClick={() => setRatingSwapId(swap.id)}
                                    data-testid="open-rate-swap-btn"
                                    className="px-3 py-1 rounded-lg bg-amber-500/20 hover:bg-amber-500 text-amber-300 hover:text-slate-950 font-bold border border-amber-500/40 transition"
                                  >
                                    ★ Rate Swap
                                  </button>
                                )}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Composer */}
            <form
              onSubmit={handleSendText}
              className="p-3 border-t border-slate-800 flex items-center gap-2"
            >
              <input
                type="text"
                value={inputBody}
                onChange={(e) => setInputBody(e.target.value)}
                placeholder="Write a message to arrange your swap..."
                data-testid="chat-input"
                className="flex-1 px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white focus:outline-none focus:border-indigo-500"
              />
              <button
                type="submit"
                data-testid="send-chat-btn"
                className="px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1"
              >
                <Send className="w-3.5 h-3.5" /> Send
              </button>
            </form>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-xs text-slate-500">
            Select a conversation to view messages &amp; propose a swap
          </div>
        )}
      </div>

      {/* Propose Swap Modal */}
      {proposeOpen && activeThread && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-slate-900 border border-slate-700 rounded-2xl p-5 shadow-2xl">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="font-bold text-white text-sm flex items-center gap-1.5">
                <ArrowLeftRight className="w-4 h-4 text-indigo-400" />
                Propose Swap with @{activeThread.otherUserDisplayName}
              </h3>
              <button
                onClick={() => setProposeOpen(false)}
                className="p-1 rounded-lg hover:bg-slate-800 text-slate-400"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSubmitSwapProposal} className="space-y-4 mt-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  My Physical Game to Offer (HAVE List)
                </label>
                <select
                  value={selectedMyListingId}
                  onChange={(e) => setSelectedMyListingId(e.target.value)}
                  required
                  data-testid="select-my-have"
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white"
                >
                  {myHaveListings.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.gameTitle} ({item.platform} • {CONDITION_LABELS[item.condition]})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  @{activeThread.otherUserDisplayName}&apos;s Game I Want (Their HAVE List)
                </label>
                <select
                  value={selectedTheirListingId}
                  onChange={(e) => setSelectedTheirListingId(e.target.value)}
                  required
                  data-testid="select-their-have"
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white"
                >
                  {theirHaveListings.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.gameTitle} ({item.platform} • {CONDITION_LABELS[item.condition]})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Suggested Bangalore Public Meetup Spot
                </label>
                <select
                  value={selectedMeetupSpotId}
                  onChange={(e) => setSelectedMeetupSpotId(e.target.value)}
                  data-testid="select-meetup-spot"
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white"
                >
                  {meetupSpots.map((spot) => (
                    <option key={spot.id} value={spot.id}>
                      {spot.name} ({spot.localityName})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1">
                  Message (Optional)
                </label>
                <input
                  type="text"
                  value={proposalNote}
                  onChange={(e) => setProposalNote(e.target.value)}
                  placeholder="e.g. Free this Saturday afternoon at the gaming cafe!"
                  className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setProposeOpen(false)}
                  className="px-4 py-2 rounded-xl bg-slate-800 text-slate-300 text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  data-testid="submit-swap-proposal-btn"
                  className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold"
                >
                  Send Swap Proposal
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Rating Modal */}
      {ratingSwapId && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-slate-900 border border-slate-700 rounded-2xl p-5 shadow-2xl">
            <h3 className="font-bold text-white text-sm mb-3">
              Rate Your Completed Swap
            </h3>
            <form onSubmit={handleSubmitRating} className="space-y-4">
              <div className="flex items-center justify-center gap-2 py-2">
                {[1, 2, 3, 4, 5].map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setRatingStars(s)}
                    className={`p-1.5 ${
                      s <= ratingStars ? "text-amber-400" : "text-slate-600"
                    }`}
                  >
                    <Star className="w-6 h-6 fill-current" />
                  </button>
                ))}
              </div>
              <textarea
                rows={2}
                value={ratingComment}
                onChange={(e) => setRatingComment(e.target.value)}
                placeholder="How was the in-person swap experience and disc condition?"
                className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white"
              />
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setRatingSwapId(null)}
                  className="px-3 py-2 rounded-xl bg-slate-800 text-slate-300 text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  data-testid="submit-rating-btn"
                  className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs"
                >
                  Submit Rating
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default function ChatPage() {
  return (
    <AppShell title="Real-Time Chat & Swap Lifecycle">
      <Suspense fallback={<div className="text-xs text-slate-400 p-4">Loading chat...</div>}>
        <ChatContent />
      </Suspense>
    </AppShell>
  );
}

