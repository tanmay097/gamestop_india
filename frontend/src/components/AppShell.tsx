"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Bell,
  Compass,
  Gamepad2,
  LogOut,
  MapPin,
  MessageSquare,
  Sparkles,
  User,
  X,
} from "lucide-react";
import { api, getStoredTokens } from "@/lib/api";
import { MyProfile, NotificationItem } from "@/lib/types";

interface AppShellProps {
  children: React.ReactNode;
  title?: string;
}

export default function AppShell({ children, title }: AppShellProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [profile, setProfile] = useState<MyProfile | null>(null);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);

  useEffect(() => {
    if (typeof window !== "undefined" && "serviceWorker" in navigator) {
      navigator.serviceWorker.register("/sw.js").catch(() => {});
    }
  }, []);

  useEffect(() => {
    const { accessToken } = getStoredTokens();
    if (!accessToken) {
      router.replace("/login");
      return;
    }
    api
      .getMyProfile()
      .then((me) => {
        setProfile(me);
        if (!me.onboardingCompleted && pathname !== "/onboarding") {
          router.replace("/onboarding");
        }
      })
      .catch(() => {
        router.replace("/login");
      });
  }, [pathname, router]);

  const openNotifications = async () => {
    setNotificationsOpen(true);
    try {
      const page = await api.getNotifications();
      setNotifications(page.items);
      if (profile && profile.unreadNotificationsCount > 0) {
        await api.markNotificationsRead();
        setProfile({ ...profile, unreadNotificationsCount: 0 });
      }
    } catch {
      // ignore
    }
  };

  const handleLogout = async () => {
    await api.logout();
    router.replace("/login");
  };

  const navItems = [
    { href: "/", label: "Matches", icon: Sparkles },
    { href: "/map", label: "Map", icon: Compass },
    { href: "/games", label: "Games", icon: Gamepad2 },
    { href: "/chat", label: "Swaps & Chat", icon: MessageSquare },
    { href: "/profile", label: "Profile", icon: User },
  ];

  return (
    <div className="min-h-screen flex flex-col max-w-4xl mx-auto bg-[#0b101d] border-x border-slate-800/70 shadow-2xl">
      {/* Top Header */}
      <header className="sticky top-0 z-30 backdrop-blur-md bg-[#0b101d]/90 border-b border-slate-800 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link href="/" className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-indigo-500 to-indigo-700 flex items-center justify-center shadow-lg shadow-indigo-500/20">
              <Gamepad2 className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-bold tracking-tight text-white text-base">
                  GameSwap
                </span>
                <span className="text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  BLR
                </span>
              </div>
              {title && (
                <p className="text-xs text-slate-400 leading-none mt-0.5">{title}</p>
              )}
            </div>
          </Link>
        </div>

        <div className="flex items-center gap-2">
          {profile?.localityName && (
            <Link
              href="/profile"
              className="hidden sm:flex items-center gap-1.5 text-xs bg-slate-800/90 hover:bg-slate-800 text-slate-200 px-2.5 py-1.5 rounded-full border border-slate-700"
            >
              <MapPin className="w-3.5 h-3.5 text-emerald-400" />
              <span>{profile.localityName}</span>
            </Link>
          )}

          <button
            onClick={openNotifications}
            aria-label="Notifications"
            className="relative p-2 rounded-xl bg-slate-800/80 hover:bg-slate-800 text-slate-300 border border-slate-700/80"
          >
            <Bell className="w-4 h-4" />
            {profile && profile.unreadNotificationsCount > 0 && (
              <span className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-rose-500 text-white text-[10px] font-bold flex items-center justify-center">
                {profile.unreadNotificationsCount}
              </span>
            )}
          </button>

          <button
            onClick={handleLogout}
            title="Sign out"
            className="p-2 rounded-xl bg-slate-800/80 hover:bg-rose-950/60 hover:text-rose-300 text-slate-400 border border-slate-700/80 transition"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 pb-24 px-4 pt-4">{children}</main>

      {/* Notifications Drawer */}
      {notificationsOpen && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex justify-end">
          <div className="w-full max-w-md bg-slate-900 h-full border-l border-slate-800 p-4 flex flex-col">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h2 className="font-semibold text-white flex items-center gap-2">
                <Bell className="w-4 h-4 text-indigo-400" />
                In-App Notifications
              </h2>
              <button
                onClick={() => setNotificationsOpen(false)}
                className="p-1.5 rounded-lg hover:bg-slate-800 text-slate-400"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto divide-y divide-slate-800/80 mt-2">
              {notifications.length === 0 ? (
                <p className="text-sm text-slate-500 text-center py-12">
                  No notifications yet. Propose a swap to get started!
                </p>
              ) : (
                notifications.map((n) => (
                  <div key={n.id} className="py-3 px-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-indigo-400">
                        {n.title}
                      </span>
                      <span className="text-[11px] text-slate-500">
                        {new Date(n.createdAt).toLocaleTimeString([], {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </span>
                    </div>
                    <p className="text-xs text-slate-300 mt-1">{n.body}</p>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {/* Mobile-First Bottom Navigation */}
      <nav className="fixed bottom-0 left-0 right-0 z-30 max-w-4xl mx-auto bg-[#0b101d]/95 backdrop-blur-md border-t border-slate-800 px-2 py-1.5">
        <div className="grid grid-cols-5 gap-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex flex-col items-center justify-center py-1.5 rounded-xl text-[11px] font-medium transition ${
                  active
                    ? "text-indigo-400 bg-indigo-500/10"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                <Icon className={`w-5 h-5 mb-0.5 ${active ? "text-indigo-400" : ""}`} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </div>
      </nav>
    </div>
  );
}

