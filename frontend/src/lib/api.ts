import {
  AuthTokensResponse,
  ChatMessage,
  ChatThread,
  ConsolePlatform,
  CursorPage,
  Game,
  GameCondition,
  GameDiscussion,
  GameReview,
  HaveListing,
  Locality,
  LocalGameOwner,
  MapPin,
  MatchCard,
  MeetupSpot,
  MyProfile,
  NotificationItem,
  OtpRequestResponse,
  PublicUserProfile,
  ShelfGame,
  Swap,
  UserRating,
  WantListing,
} from "./types";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
export const WS_BASE_URL =
  process.env.NEXT_PUBLIC_WS_URL || "ws://localhost:8080/ws";

const ACCESS_TOKEN_KEY = "gameswap_access_token";
const REFRESH_TOKEN_KEY = "gameswap_refresh_token";
const USER_ID_KEY = "gameswap_user_id";

export function getStoredTokens() {
  if (typeof window === "undefined") return { accessToken: null, refreshToken: null, userId: null };
  return {
    accessToken: localStorage.getItem(ACCESS_TOKEN_KEY),
    refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY),
    userId: localStorage.getItem(USER_ID_KEY),
  };
}

export function saveAuthSession(auth: AuthTokensResponse) {
  if (typeof window === "undefined") return;
  localStorage.setItem(ACCESS_TOKEN_KEY, auth.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken);
  localStorage.setItem(USER_ID_KEY, auth.userId);
}

export function clearAuthSession() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_ID_KEY);
}

async function request<T>(path: string, options: RequestInit = {}, retry = true): Promise<T> {
  const { accessToken, refreshToken } = getStoredTokens();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }

  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (res.status === 401 && retry && refreshToken && !path.startsWith("/auth/")) {
    try {
      const refreshRes = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      });
      if (refreshRes.ok) {
        const rotated: AuthTokensResponse = await refreshRes.json();
        saveAuthSession(rotated);
        return request<T>(path, options, false);
      } else {
        clearAuthSession();
      }
    } catch {
      clearAuthSession();
    }
  }

  if (!res.ok) {
    let errPayload: { message?: string; code?: string } = {};
    try {
      errPayload = await res.json();
    } catch {
      // ignore
    }
    const error = new Error(errPayload.message || `Request failed (${res.status})`) as Error & {
      code?: string;
      status?: number;
    };
    error.code = errPayload.code;
    error.status = res.status;
    throw error;
  }

  return res.json();
}

export const api = {
  // Auth
  requestOtp: (phone: string) =>
    request<OtpRequestResponse>("/auth/otp/request", {
      method: "POST",
      body: JSON.stringify({ phone }),
    }),
  verifyOtp: (phone: string, otp: string) =>
    request<AuthTokensResponse>("/auth/otp/verify", {
      method: "POST",
      body: JSON.stringify({ phone, otp }),
    }),
  logout: () => {
    const { refreshToken } = getStoredTokens();
    return request<{ message: string }>("/auth/logout", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    }).finally(() => clearAuthSession());
  },

  // Users & Onboarding
  getMyProfile: () => request<MyProfile>("/users/me"),
  completeOnboarding: (payload: {
    displayName: string;
    consoles: ConsolePlatform[];
    localityId: string;
    bio?: string;
  }) =>
    request<MyProfile>("/users/me/onboarding", {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  updateProfile: (payload: {
    displayName?: string;
    consoles?: ConsolePlatform[];
    localityId?: string;
    bio?: string;
  }) =>
    request<MyProfile>("/users/me", {
      method: "PATCH",
      body: JSON.stringify(payload),
    }),
  getPublicProfile: (userId: string) => request<PublicUserProfile>(`/users/${userId}/profile`),
  getLocalities: (q = "") =>
    request<CursorPage<Locality>>(`/localities?q=${encodeURIComponent(q)}&limit=30`),
  getMeetupSpots: (localityId?: string) =>
    request<CursorPage<MeetupSpot>>(
      `/meetup-spots?limit=30${localityId ? `&localityId=${localityId}` : ""}`
    ),
  getNotifications: () => request<CursorPage<NotificationItem>>("/notifications?limit=25"),
  markNotificationsRead: () =>
    request<{ message: string }>("/notifications/read-all", { method: "POST" }),

  // Games & Social
  searchGames: (q = "", platform = "", cursor?: string) => {
    const params = new URLSearchParams();
    if (q) params.set("q", q);
    if (platform) params.set("platform", platform);
    if (cursor) params.set("cursor", cursor);
    params.set("limit", "20");
    return request<CursorPage<Game>>(`/games/search?${params.toString()}`);
  },
  getGame: (gameId: string) => request<Game>(`/games/${gameId}`),
  getGameOwners: (gameId: string, platform = "") =>
    request<CursorPage<LocalGameOwner>>(
      `/games/${gameId}/owners?limit=30${platform ? `&platform=${platform}` : ""}`
    ),
  getGameReviews: (gameId: string) =>
    request<CursorPage<GameReview>>(`/games/${gameId}/reviews?limit=25`),
  postGameReview: (gameId: string, rating: number, comment: string) =>
    request<GameReview>(`/games/${gameId}/reviews`, {
      method: "POST",
      body: JSON.stringify({ rating, comment }),
    }),
  getGameDiscussions: (gameId: string) =>
    request<CursorPage<GameDiscussion>>(`/games/${gameId}/discussions?limit=30`),
  postGameDiscussion: (gameId: string, body: string) =>
    request<GameDiscussion>(`/games/${gameId}/discussions`, {
      method: "POST",
      body: JSON.stringify({ body }),
    }),

  // Listings & Shelf
  getHaveListings: (userId?: string) =>
    request<CursorPage<HaveListing>>(
      `/listings/have?limit=50${userId ? `&userId=${userId}` : ""}`
    ),
  addHaveListing: (payload: {
    gameId: string;
    platform: ConsolePlatform;
    condition: GameCondition;
    notes?: string;
  }) =>
    request<HaveListing>("/listings/have", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  removeHaveListing: (listingId: string) =>
    request<{ message: string }>(`/listings/have/${listingId}`, { method: "DELETE" }),
  getWantListings: (userId?: string) =>
    request<CursorPage<WantListing>>(
      `/listings/want?limit=20${userId ? `&userId=${userId}` : ""}`
    ),
  addWantListing: (payload: { gameId: string; platform: ConsolePlatform }) =>
    request<WantListing>("/listings/want", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  removeWantListing: (listingId: string) =>
    request<{ message: string }>(`/listings/want/${listingId}`, { method: "DELETE" }),
  setShelfStatus: (
    gameId: string,
    platform: ConsolePlatform,
    shelfStatus: "CURRENTLY_PLAYING" | "FINISHED"
  ) =>
    request<ShelfGame>(`/shelf/${gameId}`, {
      method: "PUT",
      body: JSON.stringify({ platform, shelfStatus }),
    }),
  removeFromShelf: (gameId: string) =>
    request<{ message: string }>(`/shelf/${gameId}`, { method: "DELETE" }),

  // Matching & Map
  getMatches: (radiusKm = 5, platform = "", cursor?: string) => {
    const params = new URLSearchParams();
    params.set("radiusKm", String(radiusKm));
    if (platform) params.set("platform", platform);
    if (cursor) params.set("cursor", cursor);
    params.set("limit", "15");
    return request<CursorPage<MatchCard>>(`/matches?${params.toString()}`);
  },
  getMapPins: (radiusKm = 10, platform = "", gameId = "") => {
    const params = new URLSearchParams();
    params.set("radiusKm", String(radiusKm));
    if (platform) params.set("platform", platform);
    if (gameId) params.set("gameId", gameId);
    params.set("limit", "80");
    return request<CursorPage<MapPin>>(`/map/pins?${params.toString()}`);
  },

  // Chats & Swaps
  openChatThread: (recipientId: string) =>
    request<ChatThread>("/chats/threads", {
      method: "POST",
      body: JSON.stringify({ recipientId }),
    }),
  getChatThreads: () => request<CursorPage<ChatThread>>("/chats/threads?limit=30"),
  getChatMessages: (threadId: string) =>
    request<CursorPage<ChatMessage>>(`/chats/threads/${threadId}/messages?limit=50`),
  sendChatMessage: (threadId: string, body: string, meetupSpotId?: string) =>
    request<ChatMessage>(`/chats/threads/${threadId}/messages`, {
      method: "POST",
      body: JSON.stringify({ body, meetupSpotId }),
    }),
  proposeSwap: (payload: {
    threadId: string;
    myHaveListingId: string;
    theirHaveListingId: string;
    meetupSpotId?: string;
    message?: string;
  }) =>
    request<Swap>("/swaps/propose", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  acceptSwap: (swapId: string) =>
    request<Swap>(`/swaps/${swapId}/accept`, { method: "POST" }),
  declineSwap: (swapId: string) =>
    request<Swap>(`/swaps/${swapId}/decline`, { method: "POST" }),
  cancelSwap: (swapId: string) =>
    request<Swap>(`/swaps/${swapId}/cancel`, { method: "POST" }),
  confirmSwap: (swapId: string) =>
    request<Swap>(`/swaps/${swapId}/confirm`, { method: "POST" }),
  submitRating: (swapId: string, stars: number, comment?: string) =>
    request<UserRating>(`/swaps/${swapId}/ratings`, {
      method: "POST",
      body: JSON.stringify({ stars, comment }),
    }),
  getUserRatings: (userId: string) =>
    request<CursorPage<UserRating>>(`/users/${userId}/ratings?limit=25`),

  // Safety
  blockUser: (targetUserId: string) =>
    request<{ message: string }>(`/safety/blocks/${targetUserId}`, { method: "POST" }),
  unblockUser: (targetUserId: string) =>
    request<{ message: string }>(`/safety/blocks/${targetUserId}`, { method: "DELETE" }),
  reportUser: (reportedUserId: string, reason: string, details?: string) =>
    request(`/safety/reports`, {
      method: "POST",
      body: JSON.stringify({ reportedUserId, reason, details }),
    }),
};

export const PLATFORM_LABELS: Record<ConsolePlatform, string> = {
  PS5: "PS5",
  PS4: "PS4",
  SERIES_X_S: "Xbox Series X/S",
  XBOX_ONE: "Xbox One",
  SWITCH: "Nintendo Switch",
};

export const CONDITION_LABELS: Record<GameCondition, string> = {
  LIKE_NEW: "Like New",
  GOOD: "Good",
  WORN: "Worn",
};

