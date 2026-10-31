"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { Gamepad2, Lock, MapPin, Phone, ShieldCheck, Sparkles } from "lucide-react";
import { api, saveAuthSession } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [phone, setPhone] = useState("9876543210");
  const [otp, setOtp] = useState("");
  const [step, setStep] = useState<"PHONE" | "OTP">("PHONE");
  const [devOtpHint, setDevOtpHint] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRequestOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await api.requestOtp(phone);
      setStep("OTP");
      if (res.devOtpHint) {
        setDevOtpHint(res.devOtpHint);
        setOtp(res.devOtpHint);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to send OTP");
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const auth = await api.verifyOtp(phone, otp);
      saveAuthSession(auth);
      if (auth.onboardingCompleted) {
        router.push("/");
      } else {
        router.push("/onboarding");
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Invalid OTP");
    } finally {
      setLoading(false);
    }
  };

  const quickDemoLogin = async (demoPhone: string) => {
    setError(null);
    setPhone(demoPhone);
    setLoading(true);
    try {
      const otpRes = await api.requestOtp(demoPhone);
      if (otpRes.devOtpHint) {
        const auth = await api.verifyOtp(demoPhone, otpRes.devOtpHint);
        saveAuthSession(auth);
        router.push(auth.onboardingCompleted ? "/" : "/onboarding");
      } else {
        setStep("OTP");
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Demo login failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col justify-center items-center px-4 py-10 bg-gradient-to-b from-[#090d16] via-[#0e1526] to-[#090d16]">
      <div className="w-full max-w-md bg-slate-900/90 border border-slate-800 rounded-3xl p-6 sm:p-8 shadow-2xl">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-indigo-500 to-indigo-700 flex items-center justify-center shadow-lg shadow-indigo-500/30">
            <Gamepad2 className="w-7 h-7 text-white" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-white">GameSwap</h1>
              <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                Bangalore
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Hyperlocal Physical Console Game Swapping
            </p>
          </div>
        </div>

        {error && (
          <div
            role="alert"
            className="mb-4 p-3 rounded-xl bg-rose-950/70 border border-rose-800 text-rose-200 text-xs"
          >
            {error}
          </div>
        )}

        {step === "PHONE" ? (
          <form onSubmit={handleRequestOtp} className="space-y-4">
            <div>
              <label
                htmlFor="phone-input"
                className="block text-xs font-medium text-slate-300 mb-1.5"
              >
                Indian Mobile Number
              </label>
              <div className="relative flex items-center">
                <span className="absolute left-3.5 text-xs font-semibold text-slate-400 flex items-center gap-1">
                  <Phone className="w-3.5 h-3.5 text-indigo-400" /> +91
                </span>
                <input
                  id="phone-input"
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="9876543210"
                  required
                  className="w-full pl-16 pr-4 py-3 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:border-indigo-500"
                />
              </div>
              <p className="text-[11px] text-slate-500 mt-1.5 flex items-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                Your phone number is encrypted & never shared with other users.
              </p>
            </div>

            <button
              type="submit"
              disabled={loading}
              data-testid="send-otp-btn"
              className="w-full py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-semibold text-sm shadow-lg shadow-indigo-600/30 transition"
            >
              {loading ? "Sending OTP..." : "Send 6-Digit OTP"}
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerifyOtp} className="space-y-4">
            {devOtpHint && (
              <div
                data-testid="dev-otp-banner"
                className="p-3 rounded-xl bg-emerald-950/60 border border-emerald-700/70 text-emerald-200 text-xs"
              >
                <span className="font-semibold">Dev Console OTP:</span>{" "}
                <code className="font-mono font-bold text-emerald-300">{devOtpHint}</code>{" "}
                (also logged to backend stdout)
              </div>
            )}

            <div>
              <label
                htmlFor="otp-input"
                className="block text-xs font-medium text-slate-300 mb-1.5"
              >
                Enter 6-Digit OTP sent to +91 {phone}
              </label>
              <div className="relative flex items-center">
                <Lock className="w-4 h-4 text-indigo-400 absolute left-3.5" />
                <input
                  id="otp-input"
                  type="text"
                  maxLength={6}
                  value={otp}
                  onChange={(e) => setOtp(e.target.value)}
                  placeholder="123456"
                  required
                  className="w-full pl-10 pr-4 py-3 rounded-xl bg-slate-950 border border-slate-700 text-white tracking-widest font-mono text-sm focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              data-testid="verify-otp-btn"
              className="w-full py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-semibold text-sm shadow-lg shadow-indigo-600/30 transition"
            >
              {loading ? "Verifying..." : "Verify & Continue"}
            </button>

            <button
              type="button"
              onClick={() => {
                setStep("PHONE");
                setDevOtpHint(null);
              }}
              className="w-full py-2 text-xs text-slate-400 hover:text-slate-200"
            >
              Change phone number
            </button>
          </form>
        )}

        {/* Instant Seeded Demo Accounts */}
        <div className="mt-6 pt-5 border-t border-slate-800">
          <p className="text-xs font-semibold text-slate-300 mb-2.5 flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-amber-400" />
            Instant Seeded Demo Accounts (50 BLR Gamers)
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => quickDemoLogin("9876543210")}
              data-testid="demo-login-arjun"
              className="text-left p-2.5 rounded-xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700 transition"
            >
              <div className="text-xs font-bold text-white">@arjun_blr</div>
              <div className="text-[11px] text-slate-400 flex items-center gap-1 mt-0.5">
                <MapPin className="w-3 h-3 text-emerald-400" /> Indiranagar • PS5/Switch
              </div>
            </button>
            <button
              type="button"
              onClick={() => quickDemoLogin("9876543211")}
              data-testid="demo-login-kavya"
              className="text-left p-2.5 rounded-xl bg-slate-800/80 hover:bg-slate-800 border border-slate-700 transition"
            >
              <div className="text-xs font-bold text-white">@kavya_plays</div>
              <div className="text-[11px] text-slate-400 flex items-center gap-1 mt-0.5">
                <MapPin className="w-3 h-3 text-emerald-400" /> Indiranagar • 2-Way Match
              </div>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

