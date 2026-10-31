"use client";

import React, { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import maplibregl from "maplibre-gl";
import {
  MapPin as MapPinIcon,
  MessageSquare,
  Shield,
  Star,
} from "lucide-react";
import AppShell from "@/components/AppShell";
import { api, CONDITION_LABELS, PLATFORM_LABELS } from "@/lib/api";
import { ConsolePlatform, Game, MapPin } from "@/lib/types";

export default function MapViewPage() {
  const router = useRouter();
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<maplibregl.Marker[]>([]);

  const [pins, setPins] = useState<MapPin[]>([]);
  const [games, setGames] = useState<Game[]>([]);
  const [radiusKm, setRadiusKm] = useState<number>(15);
  const [platform, setPlatform] = useState<string>("");
  const [selectedGameId, setSelectedGameId] = useState<string>("");
  const [selectedPin, setSelectedPin] = useState<MapPin | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .searchGames("", "")
      .then((res) => setGames(res.items))
      .catch(() => {});
  }, []);

  useEffect(() => {
    setLoading(true);
    api
      .getMapPins(radiusKm, platform, selectedGameId)
      .then((res) => {
        setPins(res.items);
        if (res.items.length > 0) {
          setSelectedPin(res.items[0]);
        } else {
          setSelectedPin(null);
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [radiusKm, platform, selectedGameId]);

  // Initialize MapLibre GL with OpenStreetMap raster tiles
  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) return;

    try {
      const map = new maplibregl.Map({
        container: mapContainerRef.current,
        style: {
          version: 8,
          sources: {
            osm: {
              type: "raster",
              tiles: ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              tileSize: 256,
              attribution: "&copy; OpenStreetMap contributors",
            },
          },
          layers: [
            {
              id: "osm-layer",
              type: "raster",
              source: "osm",
              minzoom: 0,
              maxzoom: 19,
            },
          ],
        },
        center: [77.6309, 12.9538], // Central Bangalore (Indiranagar / Koramangala / HSR corridor)
        zoom: 11.4,
      });

      map.addControl(new maplibregl.NavigationControl(), "top-right");
      mapRef.current = map;
    } catch {
      // Headless WebGL fallback handled gracefully by pin cards list
    }

    return () => {
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
    };
  }, []);

  // Render fuzzed ~500m pins on MapLibre map
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    markersRef.current.forEach((m) => m.remove());
    markersRef.current = [];

    pins.forEach((pin) => {
      const el = document.createElement("button");
      el.className =
        pin.matchTier === 2
          ? "px-2 py-1 rounded-full bg-emerald-600 text-white text-[10px] font-bold shadow-lg border-2 border-white cursor-pointer"
          : "px-2 py-1 rounded-full bg-indigo-600 text-white text-[10px] font-bold shadow-lg border-2 border-white cursor-pointer";
      el.textContent = `${pin.platform}`;
      el.title = `${pin.gameTitle} (@${pin.userDisplayName} - ${pin.localityName})`;
      el.onclick = () => {
        setSelectedPin(pin);
      };

      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([pin.fuzzedLongitude, pin.fuzzedLatitude])
        .addTo(map);
      markersRef.current.push(marker);
    });
  }, [pins]);

  const handleOpenChat = async (userId: string) => {
    try {
      const thread = await api.openChatThread(userId);
      router.push(`/chat?threadId=${thread.id}`);
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to open chat");
    }
  };

  return (
    <AppShell title="Bangalore Hyperlocal Swap Map">
      {/* Filter Bar */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-3.5 mb-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-1.5 text-xs text-emerald-400 font-medium">
            <Shield className="w-4 h-4 shrink-0" />
            <span>
              Privacy Protection: Every pin is offset to a ~500m fuzzed neighbourhood area (never exact home location).
            </span>
          </div>
          <span className="text-xs font-semibold text-slate-400">
            {pins.length} Active Listings Nearby
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
          <select
            aria-label="Filter by Game"
            value={selectedGameId}
            onChange={(e) => setSelectedGameId(e.target.value)}
            className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
          >
            <option value="">All Games ({games.length})</option>
            {games.map((g) => (
              <option key={g.id} value={g.id}>
                {g.title}
              </option>
            ))}
          </select>

          <select
            aria-label="Filter by Console"
            value={platform}
            onChange={(e) => setPlatform(e.target.value)}
            className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
          >
            <option value="">All Consoles</option>
            {(["PS5", "PS4", "SWITCH", "SERIES_X_S", "XBOX_ONE"] as ConsolePlatform[]).map(
              (p) => (
                <option key={p} value={p}>
                  {PLATFORM_LABELS[p]}
                </option>
              )
            )}
          </select>

          <select
            aria-label="Filter by Distance"
            value={radiusKm}
            onChange={(e) => setRadiusKm(Number(e.target.value))}
            className="px-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-xs text-white"
          >
            <option value={5}>Within 5 km</option>
            <option value={10}>Within 10 km</option>
            <option value={15}>Within 15 km</option>
            <option value={25}>Within 25 km (All Bangalore)</option>
          </select>
        </div>
      </div>

      {/* MapLibre Container */}
      <div className="relative rounded-2xl overflow-hidden border border-slate-800 h-80 sm:h-96 bg-slate-950 mb-4">
        <div
          ref={mapContainerRef}
          data-testid="maplibre-canvas"
          className="w-full h-full"
        />

        {selectedPin && (
          <div
            data-testid="selected-pin-card"
            className="absolute bottom-3 left-3 right-3 sm:left-auto sm:w-96 bg-slate-900/95 backdrop-blur-md border border-indigo-500/50 rounded-2xl p-3.5 shadow-2xl"
          >
            <div className="flex items-start justify-between gap-2">
              <div>
                <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  {selectedPin.platform} • {CONDITION_LABELS[selectedPin.condition]}
                </span>
                <h3 className="text-sm font-bold text-white mt-1">
                  {selectedPin.gameTitle}
                </h3>
                <p className="text-xs text-slate-300 flex items-center gap-1 mt-0.5">
                  <MapPinIcon className="w-3.5 h-3.5 text-emerald-400" />
                  @{selectedPin.userDisplayName} • {selectedPin.localityName} (~
                  {selectedPin.distanceKm.toFixed(1)} km)
                </p>
              </div>
              <button
                onClick={() => handleOpenChat(selectedPin.userId)}
                className="px-3 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1 shrink-0"
              >
                <MessageSquare className="w-3.5 h-3.5" />
                Swap
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Nearby Fuzzed Pin List */}
      <div className="space-y-2" data-testid="map-pins-list">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
          Nearby Fuzzed Area Listings ({pins.length})
        </h2>
        {loading ? (
          <p className="text-xs text-slate-500 py-4">Loading nearby Bangalore pins...</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            {pins.slice(0, 16).map((pin) => (
              <div
                key={pin.listingId}
                onClick={() => setSelectedPin(pin)}
                className={`p-3 rounded-xl border cursor-pointer transition flex items-center justify-between ${
                  selectedPin?.listingId === pin.listingId
                    ? "bg-indigo-950/40 border-indigo-500"
                    : "bg-slate-900/70 border-slate-800 hover:border-slate-700"
                }`}
              >
                <div className="min-w-0 pr-2">
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-bold text-white truncate">
                      {pin.gameTitle}
                    </span>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-indigo-300 font-semibold">
                      {pin.platform}
                    </span>
                  </div>
                  <div className="text-[11px] text-slate-400 flex items-center gap-2 mt-1">
                    <span>@{pin.userDisplayName}</span>
                    <span>• {pin.localityName}</span>
                    <span className="flex items-center gap-0.5 text-amber-400">
                      <Star className="w-3 h-3 fill-amber-400" />
                      {Number(pin.userRatingAvg).toFixed(1)}
                    </span>
                  </div>
                  <div className="text-[10px] text-slate-500 mt-0.5">
                    Fuzzed Pin: {pin.fuzzedLatitude.toFixed(4)}°N,{" "}
                    {pin.fuzzedLongitude.toFixed(4)}°E (~500m offset)
                  </div>
                </div>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    handleOpenChat(pin.userId);
                  }}
                  className="px-2.5 py-1.5 rounded-lg bg-indigo-600/20 hover:bg-indigo-600 text-indigo-300 hover:text-white text-xs font-semibold border border-indigo-500/30 shrink-0 transition"
                >
                  Chat
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}

