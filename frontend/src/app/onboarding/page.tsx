"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Check, Gamepad2, MapPin, Search, Shield } from "lucide-react";
import { api, PLATFORM_LABELS } from "@/lib/api";
import { ConsolePlatform, Locality } from "@/lib/types";

const ALL_PLATFORMS: ConsolePlatform[] = [
  "PS5",
  "PS4",
  "SERIES_X_S",
  "XBOX_ONE",
  "SWITCH",
];

export default function OnboardingPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [consoles, setConsoles] = useState<ConsolePlatform[]>(["PS5"]);
  const [localities, setLocalities] = useState<Locality[]>([]);
  const [localitySearch, setLocalitySearch] = useState("");
  const [selectedLocalityId, setSelectedLocalityId] = useState<string>("");
  const [bio, setBio] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .getLocalities(localitySearch)
      .then((res) => {
        setLocalities(res.items);
        if (!selectedLocalityId && res.items.length > 0) {
          setSelectedLocalityId(res.items[0].id);
        }
      })
      .catch(() => {});
  }, [localitySearch, selectedLocalityId]);

  const toggleConsole = (platform: ConsolePlatform) => {
    setConsoles((prev) =>
      prev.includes(platform)
        ? prev.filter((p) => p !== platform)
        : [...prev, platform]
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (consoles.length === 0) {
      setError("Select at least one console you own.");
      return;
    }
    if (!selectedLocalityId) {
      setError("Select your Bangalore locality.");
      return;
    }
    setLoading(true);
    try {
      await api.completeOnboarding({
        displayName,
        consoles,
        localityId: selectedLocalityId,
        bio,
      });
      router.push("/");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Onboarding failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col justify-center items-center px-4 py-8 bg-[#090d16]">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-11 h-11 rounded-2xl bg-indigo-600 flex items-center justify-center">
            <Gamepad2 className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-white">
              Set Up Your Gamer Profile
            </h1>
            <p className="text-xs text-slate-400">
              Match with console gamers near your Bangalore neighbourhood
            </p>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-3 rounded-xl bg-rose-950/70 border border-rose-800 text-rose-200 text-xs">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label
              htmlFor="display-name-input"
              className="block text-xs font-semibold text-slate-300 mb-1.5"
            >
              Gamer Handle / Display Name
            </label>
            <input
              id="display-name-input"
              type="text"
              required
              minLength={3}
              maxLength={30}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              placeholder="e.g. blr_kratos_99"
              className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:border-indigo-500"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-2">
              Consoles You Own
            </label>
            <div className="flex flex-wrap gap-2">
              {ALL_PLATFORMS.map((platform) => {
                const selected = consoles.includes(platform);
                return (
                  <button
                    key={platform}
                    type="button"
                    onClick={() => toggleConsole(platform)}
                    data-testid={`console-btn-${platform}`}
                    className={`px-3 py-2 rounded-xl text-xs font-semibold border flex items-center gap-1.5 transition ${
                      selected
                        ? "bg-indigo-600/25 border-indigo-500 text-indigo-200"
                        : "bg-slate-950 border-slate-800 text-slate-400 hover:border-slate-700"
                    }`}
                  >
                    {selected && <Check className="w-3.5 h-3.5 text-indigo-400" />}
                    {PLATFORM_LABELS[platform]}
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1.5">
              Bangalore Locality (Area Centre Only)
            </label>
            <div className="relative mb-2">
              <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-3" />
              <input
                type="text"
                value={localitySearch}
                onChange={(e) => setLocalitySearch(e.target.value)}
                placeholder="Search Indiranagar, Koramangala, HSR, Whitefield..."
                className="w-full pl-9 pr-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="max-h-44 overflow-y-auto grid grid-cols-1 sm:grid-cols-2 gap-1.5 p-1 bg-slate-950/70 rounded-xl border border-slate-800">
              {localities.map((loc) => {
                const active = selectedLocalityId === loc.id;
                return (
                  <button
                    key={loc.id}
                    type="button"
                    onClick={() => setSelectedLocalityId(loc.id)}
                    data-testid={`locality-option-${loc.name}`}
                    className={`text-left px-3 py-2 rounded-lg text-xs flex items-center justify-between transition ${
                      active
                        ? "bg-indigo-600 text-white font-semibold"
                        : "hover:bg-slate-800/80 text-slate-300"
                    }`}
                  >
                    <span className="flex items-center gap-1.5 truncate">
                      <MapPin className="w-3.5 h-3.5 shrink-0" />
                      <span className="truncate">{loc.name}</span>
                    </span>
                    <span className="text-[10px] opacity-75">{loc.pincode}</span>
                  </button>
                );
              })}
            </div>
            <p className="text-[11px] text-emerald-400/90 mt-1.5 flex items-center gap-1">
              <Shield className="w-3.5 h-3.5 shrink-0" />
              We only store your neighbourhood centre & fuzz map pins by ~500m. Never your street address.
            </p>
          </div>

          <div>
            <label
              htmlFor="bio-input"
              className="block text-xs font-semibold text-slate-300 mb-1.5"
            >
              Short Bio (Optional)
            </label>
            <textarea
              id="bio-input"
              rows={2}
              maxLength={280}
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="e.g. Weekend RPG gamer, can meet at Indiranagar Metro or Church Street."
              className="w-full px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white text-xs focus:outline-none focus:border-indigo-500"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            data-testid="complete-onboarding-btn"
            className="w-full py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-semibold text-sm shadow-lg shadow-indigo-600/30 transition"
          >
            {loading ? "Saving Profile..." : "Complete Setup & Find Matches"}
          </button>
        </form>
      </div>
    </div>
  );
}

