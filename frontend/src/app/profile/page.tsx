"use client";

import React, { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import {
  CheckCircle2,
  Disc3,
  Gamepad2,
  Heart,
  MapPin,
  Plus,
  Star,
  Trash2,
} from "lucide-react";
import AppShell from "@/components/AppShell";
import { api, CONDITION_LABELS, PLATFORM_LABELS } from "@/lib/api";
import {
  HaveListing,
  MyProfile,
  PublicUserProfile,
  UserRating,
  WantListing,
} from "@/lib/types";

export default function MyProfilePage() {
  const [me, setMe] = useState<MyProfile | null>(null);
  const [publicView, setPublicView] = useState<PublicUserProfile | null>(null);
  const [haveList, setHaveList] = useState<HaveListing[]>([]);
  const [wantList, setWantList] = useState<WantListing[]>([]);
  const [ratings, setRatings] = useState<UserRating[]>([]);

  const loadData = useCallback(async () => {
    try {
      const profile = await api.getMyProfile();
      setMe(profile);
      const [pub, h, w, r] = await Promise.all([
        api.getPublicProfile(profile.id),
        api.getHaveListings(),
        api.getWantListings(),
        api.getUserRatings(profile.id),
      ]);
      setPublicView(pub);
      setHaveList(h.items);
      setWantList(w.items);
      setRatings(r.items);
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRemoveHave = async (id: string) => {
    try {
      await api.removeHaveListing(id);
      loadData();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Could not remove listing");
    }
  };

  const handleRemoveWant = async (id: string) => {
    try {
      await api.removeWantListing(id);
      loadData();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Could not remove want");
    }
  };

  if (!me) {
    return (
      <AppShell title="My Gamer Profile">
        <div className="p-8 text-center text-xs text-slate-400">
          Loading profile...
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell title="My Gamer Profile & Lists">
      {/* Profile Header */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 mb-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="w-14 h-14 rounded-2xl bg-indigo-600/20 border border-indigo-500/40 flex items-center justify-center font-bold text-indigo-300 text-lg uppercase">
              {(me.displayName || "GM").slice(0, 2)}
            </div>
            <div>
              <h1 className="text-lg font-bold text-white" data-testid="my-display-name">
                @{me.displayName}
              </h1>
              <div className="flex flex-wrap items-center gap-3 text-xs text-slate-400 mt-1">
                <span className="flex items-center gap-1 text-emerald-400 font-medium">
                  <MapPin className="w-3.5 h-3.5" /> {me.localityName}
                </span>
                <span className="flex items-center gap-1 text-amber-400 font-semibold">
                  <Star className="w-3.5 h-3.5 fill-amber-400" />
                  {Number(me.ratingAvg).toFixed(2)} ({me.ratingCount} ratings)
                </span>
                <span>• {me.completedSwapsCount} completed swaps</span>
              </div>
              {me.bio && <p className="text-xs text-slate-300 mt-2">{me.bio}</p>}
            </div>
          </div>

          <Link
            href="/games"
            className="px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" /> Add Games
          </Link>
        </div>

        {/* Consoles Owned */}
        <div className="mt-4 pt-3 border-t border-slate-800 flex items-center gap-2 flex-wrap">
          <span className="text-xs text-slate-400">Consoles Owned:</span>
          {me.consoles.map((c) => (
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
        {/* HAVE List */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-xs font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-1.5">
              <Disc3 className="w-4 h-4" /> My HAVE List ({haveList.length})
            </h2>
            <Link href="/games" className="text-xs text-indigo-400 hover:underline">
              + Add Copy
            </Link>
          </div>
          {haveList.length === 0 ? (
            <p className="text-xs text-slate-500 py-6 text-center">
              No physical copies listed yet.
            </p>
          ) : (
            <div className="space-y-2" data-testid="my-have-list">
              {haveList.map((item) => (
                <div
                  key={item.id}
                  className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between gap-2"
                >
                  <div className="min-w-0">
                    <Link
                      href={`/games/${item.gameId}`}
                      className="text-xs font-bold text-white hover:text-indigo-300 truncate block"
                    >
                      {item.gameTitle}
                    </Link>
                    <span className="text-[10px] text-slate-400">
                      {item.platform} • {CONDITION_LABELS[item.condition]} •{" "}
                      {item.status}
                    </span>
                  </div>
                  {item.status === "ACTIVE" && (
                    <button
                      onClick={() => handleRemoveHave(item.id)}
                      className="p-1.5 rounded-lg hover:bg-rose-950 text-slate-400 hover:text-rose-400"
                      title="Remove from HAVE list"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* WANT List (Max 10) */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-xs font-bold uppercase tracking-wider text-indigo-400 flex items-center gap-1.5">
              <Heart className="w-4 h-4" /> My WANT List ({wantList.length} / 10 Max)
            </h2>
            <Link href="/games" className="text-xs text-indigo-400 hover:underline">
              + Add Want
            </Link>
          </div>
          {wantList.length === 0 ? (
            <p className="text-xs text-slate-500 py-6 text-center">
              No games on your WANT list yet.
            </p>
          ) : (
            <div className="space-y-2" data-testid="my-want-list">
              {wantList.map((item) => (
                <div
                  key={item.id}
                  className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-between gap-2"
                >
                  <div className="min-w-0">
                    <Link
                      href={`/games/${item.gameId}`}
                      className="text-xs font-bold text-white hover:text-indigo-300 truncate block"
                    >
                      {item.gameTitle}
                    </Link>
                    <span className="text-[10px] text-indigo-300">
                      {item.platform}
                    </span>
                  </div>
                  <button
                    onClick={() => handleRemoveWant(item.id)}
                    className="p-1.5 rounded-lg hover:bg-rose-950 text-slate-400 hover:text-rose-400"
                    title="Remove from WANT list"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Currently Playing & Finished Shelf */}
      {publicView && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-5">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
            <h2 className="text-xs font-bold uppercase tracking-wider text-amber-400 flex items-center gap-1.5 mb-2.5">
              <Gamepad2 className="w-4 h-4" /> Currently Playing (
              {publicView.currentlyPlaying.length})
            </h2>
            <div className="space-y-1.5">
              {publicView.currentlyPlaying.map((g) => (
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
              {publicView.finishedGames.length})
            </h2>
            <div className="space-y-1.5">
              {publicView.finishedGames.map((g) => (
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
      )}

      {/* Ratings Received */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-300 mb-3">
          Swap Ratings Received ({ratings.length})
        </h2>
        {ratings.length === 0 ? (
          <p className="text-xs text-slate-500">
            Complete an in-person swap to receive ratings from Bangalore gamers!
          </p>
        ) : (
          <div className="space-y-2">
            {ratings.map((r) => (
              <div
                key={r.id}
                className="p-3 rounded-xl bg-slate-950 border border-slate-800"
              >
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-white">
                    @{r.raterDisplayName} ({r.raterLocalityName})
                  </span>
                  <span className="text-amber-400 font-bold">
                    {"★".repeat(r.stars)}
                  </span>
                </div>
                {r.comment && (
                  <p className="text-xs text-slate-300 mt-1">{r.comment}</p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}

