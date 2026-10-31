"use client";

import React, { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  Ban,
  CheckCircle2,
  Disc3,
  Flag,
  Gamepad2,
  Heart,
  MapPin,
  MessageSquare,
  Star,
} from "lucide-react";
import AppShell from "@/components/AppShell";
import { api, CONDITION_LABELS, PLATFORM_LABELS } from "@/lib/api";
import { PublicUserProfile, UserRating } from "@/lib/types";

export default function PublicGamerProfilePage() {
  const params = useParams();
  const router = useRouter();
  const userId = params.id as string;

  const [profile, setProfile] = useState<PublicUserProfile | null>(null);
  const [ratings, setRatings] = useState<UserRating[]>([]);
  const [reportModalOpen, setReportModalOpen] = useState(false);
  const [reportReason, setReportReason] = useState("NO_SHOW");
  const [reportDetails, setReportDetails] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadUser = useCallback(async () => {
    try {
      const [p, r] = await Promise.all([
        api.getPublicProfile(userId),
        api.getUserRatings(userId),
      ]);
      setProfile(p);
      setRatings(r.items);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Gamer profile not found");
    }
  }, [userId]);

  useEffect(() => {
    loadUser();
  }, [loadUser]);

  const handleStartChat = async () => {
    try {
      const thread = await api.openChatThread(userId);
      router.push(`/chat?threadId=${thread.id}`);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to open chat");
    }
  };

  const handleBlockUser = async () => {
    if (!profile) return;
    try {
      await api.blockUser(profile.id);
      router.push("/");
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to block user");
    }
  };

  const handleReportUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!profile) return;
    try {
      await api.reportUser(profile.id, reportReason, reportDetails);
      setReportModalOpen(false);
      setReportDetails("");
      setFeedback("Report submitted. Our safety team will review it.");
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to report user");
    }
  };

  if (error) {
    return (
      <AppShell title="Gamer Profile">
        <div className="p-8 text-center text-xs text-rose-400">{error}</div>
      </AppShell>
    );
  }

  if (!profile) {
    return (
      <AppShell title="Gamer Profile">
        <div className="p-8 text-center text-xs text-slate-400">
          Loading gamer profile...
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell title={`@${profile.displayName}`}>
      {feedback && (
        <div className="mb-4 p-3 rounded-xl bg-emerald-950/80 border border-emerald-700 text-emerald-200 text-xs">
          {feedback}
        </div>
      )}

      {/* Header */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 mb-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="w-14 h-14 rounded-2xl bg-indigo-600/20 border border-indigo-500/40 flex items-center justify-center font-bold text-indigo-300 text-lg uppercase">
              {profile.displayName.slice(0, 2)}
            </div>
            <div>
              <h1 className="text-lg font-bold text-white">
                @{profile.displayName}
              </h1>
              <div className="flex flex-wrap items-center gap-3 text-xs text-slate-400 mt-1">
                <span className="flex items-center gap-1 text-emerald-400 font-medium">
                  <MapPin className="w-3.5 h-3.5" /> {profile.localityName}
                </span>
                <span className="flex items-center gap-1 text-amber-400 font-semibold">
                  <Star className="w-3.5 h-3.5 fill-amber-400" />
                  {Number(profile.ratingAvg).toFixed(2)} ({profile.ratingCount}{" "}
                  ratings)
                </span>
                <span>• {profile.completedSwapsCount} swaps</span>
              </div>
              {profile.bio && (
                <p className="text-xs text-slate-300 mt-2">{profile.bio}</p>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleStartChat}
              data-testid="profile-chat-btn"
              className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5"
            >
              <MessageSquare className="w-3.5 h-3.5" /> Chat &amp; Swap
            </button>
            <button
              onClick={() => setReportModalOpen(true)}
              data-testid="report-user-btn"
              className="p-2 rounded-xl bg-slate-800 hover:bg-amber-950/60 text-slate-300 hover:text-amber-300 border border-slate-700"
              title="Report User"
            >
              <Flag className="w-4 h-4" />
            </button>
            <button
              onClick={handleBlockUser}
              data-testid="block-user-btn"
              className="p-2 rounded-xl bg-slate-800 hover:bg-rose-950/60 text-slate-300 hover:text-rose-300 border border-slate-700"
              title="Block User"
            >
              <Ban className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="mt-4 pt-3 border-t border-slate-800 flex items-center gap-2 flex-wrap">
          <span className="text-xs text-slate-400">Consoles:</span>
          {profile.consoles.map((c) => (
            <span
              key={c}
              className="text-[11px] font-semibold px-2.5 py-0.5 rounded-lg bg-slate-800 text-indigo-300 border border-slate-700"
            >
              {PLATFORM_LABELS[c]}
            </span>
          ))}
        </div>
      </div>

      {/* HAVE & WANT Lists */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-5">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
          <h2 className="text-xs font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-1.5 mb-3">
            <Disc3 className="w-4 h-4" /> HAVE List ({profile.haveListings.length})
          </h2>
          <div className="space-y-2">
            {profile.haveListings.map((item) => (
              <div
                key={item.listingId}
                className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between text-xs"
              >
                <span className="font-bold text-white truncate mr-2">
                  {item.title}
                </span>
                <span className="text-slate-400 shrink-0">
                  {item.platform} •{" "}
                  {item.condition ? CONDITION_LABELS[item.condition] : ""}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
          <h2 className="text-xs font-bold uppercase tracking-wider text-indigo-400 flex items-center gap-1.5 mb-3">
            <Heart className="w-4 h-4" /> WANT List ({profile.wantListings.length})
          </h2>
          <div className="space-y-2">
            {profile.wantListings.map((item) => (
              <div
                key={item.listingId}
                className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between text-xs"
              >
                <span className="font-bold text-white truncate mr-2">
                  {item.title}
                </span>
                <span className="text-indigo-300 shrink-0">{item.platform}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Currently Playing & Finished */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-5">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
          <h2 className="text-xs font-bold uppercase tracking-wider text-amber-400 flex items-center gap-1.5 mb-2.5">
            <Gamepad2 className="w-4 h-4" /> Currently Playing (
            {profile.currentlyPlaying.length})
          </h2>
          <div className="space-y-1.5">
            {profile.currentlyPlaying.map((g) => (
              <div
                key={g.gameId}
                className="p-2 rounded-lg bg-slate-950 border border-slate-800 text-xs flex justify-between"
              >
                <span className="text-white font-semibold">{g.title}</span>
                <span className="text-slate-400">{g.platform}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
          <h2 className="text-xs font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-1.5 mb-2.5">
            <CheckCircle2 className="w-4 h-4" /> Finished Games (
            {profile.finishedGames.length})
          </h2>
          <div className="space-y-1.5">
            {profile.finishedGames.map((g) => (
              <div
                key={g.gameId}
                className="p-2 rounded-lg bg-slate-950 border border-slate-800 text-xs flex justify-between"
              >
                <span className="text-white font-semibold">{g.title}</span>
                <span className="text-slate-400">{g.platform}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Ratings */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-300 mb-3">
          Swap Ratings ({ratings.length})
        </h2>
        <div className="space-y-2">
          {ratings.map((r) => (
            <div
              key={r.id}
              className="p-3 rounded-xl bg-slate-950 border border-slate-800 text-xs"
            >
              <div className="flex justify-between">
                <span className="font-bold text-white">
                  @{r.raterDisplayName} ({r.raterLocalityName})
                </span>
                <span className="text-amber-400 font-bold">
                  {"★".repeat(r.stars)}
                </span>
              </div>
              {r.comment && (
                <p className="text-slate-300 mt-1">{r.comment}</p>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Report Modal */}
      {reportModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-sm bg-slate-900 border border-slate-700 rounded-2xl p-5 shadow-2xl">
            <h3 className="font-bold text-white text-sm mb-3">
              Report @{profile.displayName}
            </h3>
            <form onSubmit={handleReportUser} className="space-y-3">
              <select
                value={reportReason}
                onChange={(e) => setReportReason(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white"
              >
                <option value="NO_SHOW">Meetup No-Show</option>
                <option value="MISREPRESENTED_CONDITION">
                  Misrepresented Disc Condition
                </option>
                <option value="SPAM_OR_CASH_SOLICITATION">
                  Spam or Cash Sale Solicitation
                </option>
                <option value="HARASSMENT">Harassment / Unsafe Behaviour</option>
              </select>
              <textarea
                rows={3}
                value={reportDetails}
                onChange={(e) => setReportDetails(e.target.value)}
                placeholder="Provide context..."
                className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white"
              />
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setReportModalOpen(false)}
                  className="px-3 py-2 rounded-xl bg-slate-800 text-slate-300 text-xs"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-semibold text-xs"
                >
                  Submit Report
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </AppShell>
  );
}

