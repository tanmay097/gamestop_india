"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  CheckCircle2,
  Disc3,
  Heart,
  MessageSquare,
  Search,
  Star,
  Users,
  X,
} from "lucide-react";
import AppShell from "@/components/AppShell";
import { api, CONDITION_LABELS, PLATFORM_LABELS } from "@/lib/api";
import { ConsolePlatform, Game, GameCondition } from "@/lib/types";

export default function GamesCataloguePage() {
  const [games, setGames] = useState<Game[]>([]);
  const [query, setQuery] = useState("");
  const [platform, setPlatform] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Add to HAVE / WANT modal state
  const [modalGame, setModalGame] = useState<Game | null>(null);
  const [modalMode, setModalMode] = useState<"HAVE" | "WANT">("HAVE");
  const [selectedPlatform, setSelectedPlatform] = useState<ConsolePlatform>("PS5");
  const [selectedCondition, setSelectedCondition] = useState<GameCondition>("LIKE_NEW");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const fetchGames = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.searchGames(query, platform);
      setGames(res.items);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to search games");
    } finally {
      setLoading(false);
    }
  }, [query, platform]);

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchGames();
    }, 200);
    return () => clearTimeout(timer);
  }, [fetchGames]);

  const openModal = (game: Game, mode: "HAVE" | "WANT") => {
    setModalGame(game);
    setModalMode(mode);
    setSelectedPlatform(
      (game.supportedPlatforms?.[0] as ConsolePlatform) || "PS5"
    );
    setSelectedCondition("LIKE_NEW");
    setNotes("");
    setError(null);
  };

  const handleConfirmAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!modalGame) return;
    setSubmitting(true);
    setError(null);
    try {
      if (modalMode === "HAVE") {
        await api.addHaveListing({
          gameId: modalGame.id,
          platform: selectedPlatform,
          condition: selectedCondition,
          notes,
        });
        setToast(`Added "${modalGame.title}" (${selectedPlatform}) to your HAVE list!`);
      } else {
        await api.addWantListing({
          gameId: modalGame.id,
          platform: selectedPlatform,
        });
        setToast(`Added "${modalGame.title}" (${selectedPlatform}) to your WANT list!`);
      }
      setModalGame(null);
      fetchGames();
      setTimeout(() => setToast(null), 4000);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Could not add listing");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AppShell title="IGDB Game Catalogue & Local Copies">
      {toast && (
        <div
          data-testid="listing-toast"
          className="mb-4 p-3 rounded-xl bg-emerald-950/80 border border-emerald-600 text-emerald-200 text-xs flex items-center gap-2"
        >
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{toast}</span>
        </div>
      )}

      {/* Search & Platform Filter */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 mb-5 space-y-3">
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search console games (e.g. Elden Ring, Spider-Man 2, Zelda, Astro Bot)..."
            data-testid="game-search-input"
            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-sm text-white focus:outline-none focus:border-indigo-500"
          />
        </div>

        <div className="flex items-center gap-1.5 flex-wrap">
          <span className="text-xs text-slate-400 mr-1">Platform:</span>
          {["", "PS5", "PS4", "SWITCH", "SERIES_X_S", "XBOX_ONE"].map((p) => (
            <button
              key={p || "ALL"}
              onClick={() => setPlatform(p)}
              className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition ${
                platform === p
                  ? "bg-indigo-600 text-white"
                  : "bg-slate-800 text-slate-400 hover:text-slate-200"
              }`}
            >
              {p ? PLATFORM_LABELS[p as ConsolePlatform] : "All Consoles"}
            </button>
          ))}
        </div>
      </div>

      {/* Games Grid */}
      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {[1, 2, 3, 4].map((n) => (
            <div
              key={n}
              className="h-36 rounded-2xl bg-slate-900/60 border border-slate-800 animate-pulse"
            />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5" data-testid="games-grid">
          {games.map((g) => (
            <div
              key={g.id}
              data-testid={`game-card-${g.slug}`}
              className="bg-slate-900/90 border border-slate-800 rounded-2xl p-3.5 flex gap-3.5 hover:border-slate-700 transition"
            >
              <Link
                href={`/games/${g.id}`}
                className="w-20 h-28 rounded-xl bg-slate-800 overflow-hidden shrink-0 border border-slate-700 flex items-center justify-center"
              >
                {g.coverUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={g.coverUrl}
                    alt={g.title}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <Disc3 className="w-8 h-8 text-slate-600" />
                )}
              </Link>

              <div className="flex-1 min-w-0 flex flex-col justify-between">
                <div>
                  <Link
                    href={`/games/${g.id}`}
                    className="font-bold text-sm text-white hover:text-indigo-300 line-clamp-1"
                  >
                    {g.title}
                  </Link>
                  <div className="flex items-center gap-2 text-xs text-slate-400 mt-0.5">
                    {g.releaseYear && <span>{g.releaseYear}</span>}
                    <span className="flex items-center gap-0.5 text-amber-400">
                      <Star className="w-3 h-3 fill-amber-400" />
                      {Number(g.avgReviewRating).toFixed(1)} ({g.reviewCount})
                    </span>
                    <span className="flex items-center gap-1 text-emerald-400">
                      <Users className="w-3 h-3" />
                      {g.activeLocalCopiesCount} in BLR
                    </span>
                  </div>
                  <div className="flex flex-wrap gap-1 mt-1.5">
                    {g.supportedPlatforms.map((plat) => (
                      <span
                        key={plat}
                        className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 font-medium"
                      >
                        {plat}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="flex items-center gap-1.5 mt-2.5">
                  <button
                    onClick={() => openModal(g, "HAVE")}
                    data-testid={`add-have-btn-${g.slug}`}
                    className="flex-1 py-1.5 px-2 rounded-lg bg-emerald-600/20 hover:bg-emerald-600 text-emerald-300 hover:text-white border border-emerald-500/30 text-[11px] font-semibold flex items-center justify-center gap-1 transition"
                  >
                    <Disc3 className="w-3 h-3" /> + I Have
                  </button>
                  <button
                    onClick={() => openModal(g, "WANT")}
                    data-testid={`add-want-btn-${g.slug}`}
                    className="flex-1 py-1.5 px-2 rounded-lg bg-indigo-600/20 hover:bg-indigo-600 text-indigo-300 hover:text-white border border-indigo-500/30 text-[11px] font-semibold flex items-center justify-center gap-1 transition"
                  >
                    <Heart className="w-3 h-3" /> + I Want
                  </button>
                  <Link
                    href={`/games/${g.id}`}
                    className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700"
                    title="Reviews, Local Owners & Discussion"
                  >
                    <MessageSquare className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal for Adding to HAVE / WANT */}
      {modalGame && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-slate-900 border border-slate-700 rounded-2xl p-5 shadow-2xl">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h3 className="font-bold text-white text-sm">
                {modalMode === "HAVE"
                  ? `List Physical Copy (HAVE): ${modalGame.title}`
                  : `Add to WANT List (Max 10): ${modalGame.title}`}
              </h3>
              <button
                onClick={() => setModalGame(null)}
                className="p-1 rounded-lg hover:bg-slate-800 text-slate-400"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {error && (
              <div className="mt-3 p-2.5 rounded-xl bg-rose-950/70 border border-rose-800 text-rose-200 text-xs">
                {error}
              </div>
            )}

            <form onSubmit={handleConfirmAdd} className="space-y-4 mt-4">
              <div>
                <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                  Console Platform
                </label>
                <div className="flex flex-wrap gap-1.5">
                  {modalGame.supportedPlatforms.map((p) => (
                    <button
                      key={p}
                      type="button"
                      onClick={() => setSelectedPlatform(p)}
                      className={`px-3 py-1.5 rounded-xl text-xs font-semibold border ${
                        selectedPlatform === p
                          ? "bg-indigo-600 border-indigo-500 text-white"
                          : "bg-slate-950 border-slate-800 text-slate-400"
                      }`}
                    >
                      {PLATFORM_LABELS[p]}
                    </button>
                  ))}
                </div>
              </div>

              {modalMode === "HAVE" && (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                      Physical Disc / Cartridge Condition
                    </label>
                    <div className="grid grid-cols-3 gap-2">
                      {(["LIKE_NEW", "GOOD", "WORN"] as GameCondition[]).map((c) => (
                        <button
                          key={c}
                          type="button"
                          onClick={() => setSelectedCondition(c)}
                          className={`py-2 rounded-xl text-xs font-semibold border ${
                            selectedCondition === c
                              ? "bg-emerald-600/30 border-emerald-500 text-emerald-200"
                              : "bg-slate-950 border-slate-800 text-slate-400"
                          }`}
                        >
                          {CONDITION_LABELS[c]}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">
                      Copy Notes (Optional)
                    </label>
                    <input
                      type="text"
                      maxLength={200}
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      placeholder="e.g. Original box included, zero scratches"
                      className="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-xs text-white"
                    />
                  </div>
                </>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setModalGame(null)}
                  className="px-4 py-2 rounded-xl bg-slate-800 text-slate-300 text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  data-testid="confirm-add-listing-btn"
                  className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold"
                >
                  {submitting
                    ? "Saving..."
                    : modalMode === "HAVE"
                    ? "Add to HAVE List"
                    : "Add to WANT List"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </AppShell>
  );
}

