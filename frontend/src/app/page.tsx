"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  ArrowLeftRight,
  Compass,
  MapPin,
  MessageSquare,
  PlusCircle,
  Sparkles,
  Star,
} from "lucide-react";
import AppShell from "@/components/AppShell";
import { api, CONDITION_LABELS, PLATFORM_LABELS } from "@/lib/api";
import { ConsolePlatform, MatchCard } from "@/lib/types";

export default function MatchesDashboardPage() {
  const router = useRouter();
  const [matches, setMatches] = useState<MatchCard[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [radiusKm, setRadiusKm] = useState<number>(10);
  const [platform, setPlatform] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadMatches = useCallback(
    async (reset = true, cursor?: string) => {
      if (reset) {
        setLoading(true);
        setError(null);
      } else {
        setLoadingMore(true);
      }
      try {
        const res = await api.getMatches(radiusKm, platform, cursor);
        setMatches((prev) => (reset ? res.items : [...prev, ...res.items]));
        setNextCursor(res.nextCursor);
        setHasMore(res.hasMore);
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : "Failed to load matches");
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [radiusKm, platform]
  );

  useEffect(() => {
    loadMatches(true);
  }, [loadMatches]);

  const handleStartChat = async (partnerUserId: string) => {
    try {
      const thread = await api.openChatThread(partnerUserId);
      router.push(`/chat?threadId=${thread.id}`);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Could not open chat");
    }
  };

  return (
    <AppShell title="Hyperlocal Match Discovery">
      {/* Top Banner & Filters */}
      <div className="bg-gradient-to-r from-indigo-950/80 via-slate-900 to-slate-900 border border-indigo-500/30 rounded-2xl p-4 mb-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h1 className="text-base font-bold text-white flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-amber-400" />
              Nearby Swap Matches in Bangalore
            </h1>
            <p className="text-xs text-slate-400 mt-0.5">
              Ranked by 2-Way Matches first (they have what you want &amp; want what you have), then 1-Way, then PostGIS distance.
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <Link
              href="/games"
              className="px-3 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow-sm transition"
            >
              <PlusCircle className="w-3.5 h-3.5" />
              Add HAVE / WANT
            </Link>
            <Link
              href="/map"
              className="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-1.5 border border-slate-700 transition"
            >
              <Compass className="w-3.5 h-3.5 text-emerald-400" />
              Map View
            </Link>
          </div>
        </div>

        {/* Filter Controls */}
        <div className="mt-4 pt-3 border-t border-slate-800/80 flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-slate-400">Radius:</span>
            {[5, 10, 15, 25].map((r) => (
              <button
                key={r}
                onClick={() => setRadiusKm(r)}
                data-testid={`radius-${r}km`}
                className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition ${
                  radiusKm === r
                    ? "bg-indigo-600 text-white"
                    : "bg-slate-800 text-slate-400 hover:text-slate-200"
                }`}
              >
                {r} km
              </button>
            ))}
          </div>

          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="text-xs text-slate-400">Console:</span>
            {["", "PS5", "PS4", "SWITCH", "SERIES_X_S", "XBOX_ONE"].map((plat) => (
              <button
                key={plat || "ALL"}
                onClick={() => setPlatform(plat)}
                className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition ${
                  platform === plat
                    ? "bg-emerald-600 text-white"
                    : "bg-slate-800 text-slate-400 hover:text-slate-200"
                }`}
              >
                {plat ? PLATFORM_LABELS[plat as ConsolePlatform] : "All"}
              </button>
            ))}
          </div>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-950/60 border border-rose-800 text-rose-200 text-xs mb-4">
          {error}
        </div>
      )}

      {/* Match Cards List */}
      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((n) => (
            <div
              key={n}
              className="h-44 rounded-2xl bg-slate-900/60 border border-slate-800 animate-pulse"
            />
          ))}
        </div>
      ) : matches.length === 0 ? (
        <div className="text-center py-14 px-4 rounded-2xl bg-slate-900/50 border border-slate-800">
          <ArrowLeftRight className="w-8 h-8 text-indigo-400 mx-auto mb-2" />
          <h2 className="text-sm font-bold text-white">
            No matches within {radiusKm} km yet
          </h2>
          <p className="text-xs text-slate-400 max-w-md mx-auto mt-1">
            Add physical games to your HAVE and WANT lists or increase the search radius to match with Bangalore gamers nearby!
          </p>
          <div className="mt-4 flex justify-center gap-2">
            <button
              onClick={() => setRadiusKm(25)}
              className="px-3.5 py-2 rounded-xl bg-slate-800 text-slate-200 text-xs font-semibold border border-slate-700"
            >
              Expand to 25 km
            </button>
            <Link
              href="/games"
              className="px-3.5 py-2 rounded-xl bg-indigo-600 text-white text-xs font-semibold"
            >
              Browse Games Catalogue
            </Link>
          </div>
        </div>
      ) : (
        <div className="space-y-4" data-testid="matches-list">
          {matches.map((m) => {
            const isTwoWay = m.matchType === "TWO_WAY";
            return (
              <div
                key={m.userId}
                data-testid={`match-card-${m.displayName}`}
                className={`rounded-2xl p-4 border transition ${
                  isTwoWay
                    ? "bg-gradient-to-br from-slate-900 via-slate-900 to-indigo-950/40 border-indigo-500/50 shadow-lg shadow-indigo-950/30"
                    : "bg-slate-900/80 border-slate-800"
                }`}
              >
                {/* Header */}
                <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-slate-800/80">
                  <div className="flex items-center gap-3">
                    <Link
                      href={`/users/${m.userId}`}
                      className="w-10 h-10 rounded-xl bg-indigo-600/20 border border-indigo-500/40 flex items-center justify-center font-bold text-indigo-300 text-sm uppercase"
                    >
                      {m.displayName.slice(0, 2)}
                    </Link>
                    <div>
                      <div className="flex items-center gap-2">
                        <Link
                          href={`/users/${m.userId}`}
                          className="font-bold text-sm text-white hover:text-indigo-300"
                        >
                          @{m.displayName}
                        </Link>
                        <span
                          className={`text-[10px] font-bold px-2 py-0.5 rounded-full uppercase ${
                            isTwoWay
                              ? "bg-emerald-500/20 text-emerald-300 border border-emerald-500/40"
                              : "bg-amber-500/15 text-amber-300 border border-amber-500/30"
                          }`}
                        >
                          {isTwoWay ? "2-Way Match" : "1-Way Match"}
                        </span>
                      </div>
                      <div className="flex items-center gap-2.5 text-xs text-slate-400 mt-0.5">
                        <span className="flex items-center gap-1 text-slate-300">
                          <MapPin className="w-3.5 h-3.5 text-emerald-400" />
                          {m.localityName} ({m.distanceKm.toFixed(1)} km)
                        </span>
                        <span className="flex items-center gap-0.5 text-amber-400">
                          <Star className="w-3.5 h-3.5 fill-amber-400" />
                          {Number(m.ratingAvg).toFixed(1)} ({m.ratingCount})
                        </span>
                        <span>• {m.completedSwapsCount} swaps</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <Link
                      href={`/users/${m.userId}`}
                      className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium border border-slate-700"
                    >
                      Profile
                    </Link>
                    <button
                      onClick={() => handleStartChat(m.userId)}
                      data-testid={`chat-swap-btn-${m.displayName}`}
                      className="px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow-md shadow-indigo-600/20"
                    >
                      <MessageSquare className="w-3.5 h-3.5" />
                      Chat &amp; Swap
                    </button>
                  </div>
                </div>

                {/* Matched Games Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-3">
                  {/* Column 1: What They Have that You Want */}
                  <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800/90">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-emerald-400 mb-2">
                      They Have (On Your WANT List)
                    </div>
                    {m.theyHaveGamesIWant.length > 0 ? (
                      <div className="space-y-1.5">
                        {m.theyHaveGamesIWant.map((g) => (
                          <div
                            key={g.listingId}
                            className="flex items-center justify-between text-xs bg-slate-900 px-2.5 py-1.5 rounded-lg border border-slate-800"
                          >
                            <span className="font-semibold text-white truncate mr-2">
                              {g.title}
                            </span>
                            <span className="text-[10px] text-slate-400 shrink-0">
                              {g.platform} • {CONDITION_LABELS[g.condition]}
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-slate-500 italic">
                        No direct match on your want list — see their other{" "}
                        {m.otherHaveListings.length} physical games on their profile.
                      </p>
                    )}
                  </div>

                  {/* Column 2: What They Want that You Have */}
                  <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800/90">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-indigo-400 mb-2">
                      They Want (On Your HAVE List)
                    </div>
                    {m.theyWantGamesIHave.length > 0 ? (
                      <div className="space-y-1.5">
                        {m.theyWantGamesIHave.map((g) => (
                          <div
                            key={`${g.listingId}-${g.gameId}`}
                            className="flex items-center justify-between text-xs bg-slate-900 px-2.5 py-1.5 rounded-lg border border-slate-800"
                          >
                            <span className="font-semibold text-white truncate mr-2">
                              {g.title}
                            </span>
                            <span className="text-[10px] text-slate-400 shrink-0">
                              {g.platform} • Your Copy
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-slate-500 italic">
                        1-Way Match (they have a game you want). Propose any of your HAVE games!
                      </p>
                    )}
                  </div>
                </div>
              </div>
            );
          })}

          {hasMore && nextCursor && (
            <div className="pt-2 text-center">
              <button
                onClick={() => loadMatches(false, nextCursor)}
                disabled={loadingMore}
                className="px-5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700"
              >
                {loadingMore ? "Loading more..." : "Load More Nearby Matches"}
              </button>
            </div>
          )}
        </div>
      )}
    </AppShell>
  );
}

