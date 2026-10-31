"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft,
  CheckCircle2,
  Gamepad2,
  MapPin,
  MessageSquare,
  Send,
  Star,
  Users,
} from "lucide-react";
import AppShell from "@/components/AppShell";
import { api, CONDITION_LABELS } from "@/lib/api";
import {
  ConsolePlatform,
  Game,
  GameDiscussion,
  GameReview,
  LocalGameOwner,
} from "@/lib/types";

export default function GameDetailPage() {
  const params = useParams();
  const router = useRouter();
  const gameId = params.id as string;

  const [game, setGame] = useState<Game | null>(null);
  const [owners, setOwners] = useState<LocalGameOwner[]>([]);
  const [reviews, setReviews] = useState<GameReview[]>([]);
  const [discussions, setDiscussions] = useState<GameDiscussion[]>([]);
  const [activeTab, setActiveTab] = useState<"OWNERS" | "REVIEWS" | "DISCUSSIONS">(
    "OWNERS"
  );

  // Review form
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState("");

  // Discussion form
  const [discussionBody, setDiscussionBody] = useState("");
  const [shelfToast, setShelfToast] = useState<string | null>(null);

  const loadGameHub = useCallback(async () => {
    try {
      const [g, o, r, d] = await Promise.all([
        api.getGame(gameId),
        api.getGameOwners(gameId),
        api.getGameReviews(gameId),
        api.getGameDiscussions(gameId),
      ]);
      setGame(g);
      setOwners(o.items);
      setReviews(r.items);
      setDiscussions(d.items);
    } catch {
      // ignore
    }
  }, [gameId]);

  useEffect(() => {
    loadGameHub();
  }, [loadGameHub]);

  const handleSetShelf = async (status: "CURRENTLY_PLAYING" | "FINISHED") => {
    if (!game) return;
    const plat: ConsolePlatform = game.supportedPlatforms[0] || "PS5";
    try {
      await api.setShelfStatus(game.id, plat, status);
      setShelfToast(
        status === "CURRENTLY_PLAYING"
          ? `Marked "${game.title}" as Currently Playing!`
          : `Marked "${game.title}" as Finished!`
      );
      setTimeout(() => setShelfToast(null), 3500);
    } catch {
      // ignore
    }
  };

  const handleSubmitReview = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reviewComment.trim()) return;
    try {
      await api.postGameReview(gameId, reviewRating, reviewComment);
      setReviewComment("");
      loadGameHub();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to post review");
    }
  };

  const handleSubmitDiscussion = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!discussionBody.trim()) return;
    try {
      await api.postGameDiscussion(gameId, discussionBody);
      setDiscussionBody("");
      loadGameHub();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to post comment");
    }
  };

  const handleStartChat = async (ownerId: string) => {
    try {
      const thread = await api.openChatThread(ownerId);
      router.push(`/chat?threadId=${thread.id}`);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to open chat");
    }
  };

  if (!game) {
    return (
      <AppShell title="Game Hub">
        <div className="p-8 text-center text-xs text-slate-400">
          Loading game hub...
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell title={game.title}>
      <div className="mb-3">
        <Link
          href="/games"
          className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-white"
        >
          <ArrowLeft className="w-3.5 h-3.5" /> Back to Games Catalogue
        </Link>
      </div>

      {shelfToast && (
        <div className="mb-3 p-3 rounded-xl bg-emerald-950/80 border border-emerald-600 text-emerald-200 text-xs flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          <span>{shelfToast}</span>
        </div>
      )}

      {/* Game Hero Header */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 sm:p-5 mb-5 flex flex-col sm:flex-row gap-4">
        {game.coverUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={game.coverUrl}
            alt={game.title}
            className="w-28 h-40 object-cover rounded-xl border border-slate-700 shrink-0"
          />
        )}
        <div className="flex-1 flex flex-col justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-lg font-bold text-white">{game.title}</h1>
              {game.releaseYear && (
                <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                  {game.releaseYear}
                </span>
              )}
            </div>
            <p className="text-xs text-slate-300 mt-2 leading-relaxed">
              {game.summary}
            </p>
            <div className="flex flex-wrap items-center gap-3 mt-3 text-xs">
              <span className="flex items-center gap-1 text-amber-400 font-semibold">
                <Star className="w-4 h-4 fill-amber-400" />
                {Number(game.avgReviewRating).toFixed(2)} ({game.reviewCount} reviews)
              </span>
              <span className="flex items-center gap-1 text-emerald-400 font-semibold">
                <Users className="w-4 h-4" />
                {owners.length} Physical Copies in Bangalore
              </span>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2 mt-4 pt-3 border-t border-slate-800">
            <span className="text-xs text-slate-400">Your Shelf:</span>
            <button
              onClick={() => handleSetShelf("CURRENTLY_PLAYING")}
              className="px-3 py-1.5 rounded-xl bg-indigo-600/20 hover:bg-indigo-600 text-indigo-300 hover:text-white text-xs font-semibold border border-indigo-500/30 flex items-center gap-1 transition"
            >
              <Gamepad2 className="w-3.5 h-3.5" /> Currently Playing
            </button>
            <button
              onClick={() => handleSetShelf("FINISHED")}
              className="px-3 py-1.5 rounded-xl bg-emerald-600/20 hover:bg-emerald-600 text-emerald-300 hover:text-white text-xs font-semibold border border-emerald-500/30 flex items-center gap-1 transition"
            >
              <CheckCircle2 className="w-3.5 h-3.5" /> Finished
            </button>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-4 border-b border-slate-800 pb-2">
        <button
          onClick={() => setActiveTab("OWNERS")}
          className={`px-3.5 py-2 rounded-xl text-xs font-semibold transition ${
            activeTab === "OWNERS"
              ? "bg-indigo-600 text-white"
              : "bg-slate-900 text-slate-400 hover:text-white"
          }`}
        >
          Local Owners ({owners.length})
        </button>
        <button
          onClick={() => setActiveTab("REVIEWS")}
          data-testid="tab-reviews"
          className={`px-3.5 py-2 rounded-xl text-xs font-semibold transition ${
            activeTab === "REVIEWS"
              ? "bg-indigo-600 text-white"
              : "bg-slate-900 text-slate-400 hover:text-white"
          }`}
        >
          Short Reviews ({reviews.length})
        </button>
        <button
          onClick={() => setActiveTab("DISCUSSIONS")}
          data-testid="tab-discussions"
          className={`px-3.5 py-2 rounded-xl text-xs font-semibold transition ${
            activeTab === "DISCUSSIONS"
              ? "bg-indigo-600 text-white"
              : "bg-slate-900 text-slate-400 hover:text-white"
          }`}
        >
          Discussion Thread ({discussions.length})
        </button>
      </div>

      {/* Tab Content */}
      {activeTab === "OWNERS" && (
        <div className="space-y-2.5">
          {owners.length === 0 ? (
            <p className="text-xs text-slate-500 py-8 text-center">
              No active physical copies listed in Bangalore right now.
            </p>
          ) : (
            owners.map((o) => (
              <div
                key={o.listingId}
                className="p-3.5 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-between gap-3"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <Link
                      href={`/users/${o.userId}`}
                      className="font-bold text-sm text-white hover:text-indigo-300"
                    >
                      @{o.displayName}
                    </Link>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300">
                      {o.platform} • {CONDITION_LABELS[o.condition]}
                    </span>
                  </div>
                  <div className="text-xs text-slate-400 flex items-center gap-2 mt-1">
                    <span className="flex items-center gap-1 text-slate-300">
                      <MapPin className="w-3.5 h-3.5 text-emerald-400" />
                      {o.localityName}
                      {o.distanceKm !== null && ` (${o.distanceKm.toFixed(1)} km)`}
                    </span>
                    <span className="flex items-center gap-0.5 text-amber-400">
                      <Star className="w-3 h-3 fill-amber-400" />
                      {Number(o.userRatingAvg).toFixed(1)}
                    </span>
                  </div>
                  {o.notes && (
                    <p className="text-xs text-slate-400 mt-1 italic">
                      &ldquo;{o.notes}&rdquo;
                    </p>
                  )}
                </div>

                <button
                  onClick={() => handleStartChat(o.userId)}
                  className="px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5 shrink-0"
                >
                  <MessageSquare className="w-3.5 h-3.5" />
                  Chat to Swap
                </button>
              </div>
            ))
          )}
        </div>
      )}

      {activeTab === "REVIEWS" && (
        <div className="space-y-4">
          <form
            onSubmit={handleSubmitReview}
            className="p-4 rounded-2xl bg-slate-900 border border-slate-800 space-y-3"
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-white">
                Write a Short Review
              </span>
              <div className="flex items-center gap-1">
                {[1, 2, 3, 4, 5].map((star) => (
                  <button
                    key={star}
                    type="button"
                    onClick={() => setReviewRating(star)}
                    className={`p-1 ${
                      star <= reviewRating ? "text-amber-400" : "text-slate-600"
                    }`}
                  >
                    <Star className="w-4 h-4 fill-current" />
                  </button>
                ))}
              </div>
            </div>
            <textarea
              rows={2}
              value={reviewComment}
              onChange={(e) => setReviewComment(e.target.value)}
              placeholder="Share your thoughts on performance, story, or physical disc extras..."
              required
              className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
            />
            <button
              type="submit"
              className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold"
            >
              Post Review
            </button>
          </form>

          <div className="space-y-2.5">
            {reviews.map((r) => (
              <div
                key={r.id}
                className="p-3.5 rounded-2xl bg-slate-900/80 border border-slate-800"
              >
                <div className="flex items-center justify-between">
                  <Link
                    href={`/users/${r.userId}`}
                    className="text-xs font-bold text-white hover:text-indigo-300"
                  >
                    @{r.userDisplayName}{" "}
                    <span className="font-normal text-slate-400">
                      • {r.userLocalityName}
                    </span>
                  </Link>
                  <span className="text-xs text-amber-400 font-bold">
                    {"★".repeat(r.rating)}
                  </span>
                </div>
                <p className="text-xs text-slate-300 mt-1.5">{r.comment}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeTab === "DISCUSSIONS" && (
        <div className="space-y-4">
          <form
            onSubmit={handleSubmitDiscussion}
            className="flex gap-2 p-3 rounded-2xl bg-slate-900 border border-slate-800"
          >
            <input
              type="text"
              value={discussionBody}
              onChange={(e) => setDiscussionBody(e.target.value)}
              placeholder="Ask Bangalore gamers about DLCs, edition differences, or swaps..."
              required
              className="flex-1 px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
            />
            <button
              type="submit"
              className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1"
            >
              <Send className="w-3.5 h-3.5" /> Post
            </button>
          </form>

          <div className="space-y-2.5">
            {discussions.map((d) => (
              <div
                key={d.id}
                className="p-3.5 rounded-2xl bg-slate-900/80 border border-slate-800"
              >
                <div className="flex items-center justify-between text-xs">
                  <Link
                    href={`/users/${d.userId}`}
                    className="font-bold text-indigo-300 hover:underline"
                  >
                    @{d.userDisplayName} ({d.userLocalityName})
                  </Link>
                  <span className="text-[11px] text-slate-500">
                    {new Date(d.createdAt).toLocaleDateString()}
                  </span>
                </div>
                <p className="text-xs text-slate-200 mt-1.5">{d.body}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </AppShell>
  );
}

