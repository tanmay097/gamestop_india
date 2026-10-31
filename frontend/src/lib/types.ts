export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

export type ConsolePlatform = "PS5" | "PS4" | "SERIES_X_S" | "XBOX_ONE" | "SWITCH";
export type GameCondition = "LIKE_NEW" | "GOOD" | "WORN";

export interface OtpRequestResponse {
  message: string;
  expiresInSeconds: number;
  provider: string;
  devOtpHint?: string | null;
}

export interface AuthTokensResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  userId: string;
  displayName: string | null;
  onboardingCompleted: boolean;
}

export interface Locality {
  id: string;
  name: string;
  pincode: string;
  latitude: number;
  longitude: number;
}

export interface MeetupSpot {
  id: string;
  name: string;
  category: "MALL" | "GAMING_CAFE" | "METRO_STATION" | "BOOKSTORE";
  localityId: string;
  localityName: string;
  addressHint: string;
  latitude: number;
  longitude: number;
}

export interface MyProfile {
  id: string;
  displayName: string | null;
  avatarSeed: string;
  bio: string | null;
  consoles: ConsolePlatform[];
  localityId: string | null;
  localityName: string | null;
  ratingAvg: number;
  ratingCount: number;
  completedSwapsCount: number;
  onboardingCompleted: boolean;
  activeHaveCount: number;
  activeWantCount: number;
  unreadNotificationsCount: number;
  createdAt: string;
}

export interface ShelfGame {
  gameId: string;
  title: string;
  coverUrl: string | null;
  platform: ConsolePlatform;
  shelfStatus: "CURRENTLY_PLAYING" | "FINISHED";
  updatedAt: string;
}

export interface ProfileListingItem {
  listingId: string;
  gameId: string;
  title: string;
  coverUrl: string | null;
  platform: ConsolePlatform;
  condition: GameCondition | null;
  notes: string | null;
  createdAt: string;
}

export interface PublicUserProfile {
  id: string;
  displayName: string;
  avatarSeed: string;
  bio: string | null;
  consoles: ConsolePlatform[];
  localityName: string;
  ratingAvg: number;
  ratingCount: number;
  completedSwapsCount: number;
  currentlyPlaying: ShelfGame[];
  finishedGames: ShelfGame[];
  haveListings: ProfileListingItem[];
  wantListings: ProfileListingItem[];
  joinedAt: string;
}

export interface Game {
  id: string;
  igdbId: number;
  slug: string;
  title: string;
  coverUrl: string | null;
  summary: string | null;
  releaseYear: number | null;
  genres: string[];
  supportedPlatforms: ConsolePlatform[];
  avgReviewRating: number;
  reviewCount: number;
  activeLocalCopiesCount: number;
}

export interface LocalGameOwner {
  listingId: string;
  userId: string;
  displayName: string;
  avatarSeed: string;
  localityName: string;
  userRatingAvg: number;
  userRatingCount: number;
  platform: ConsolePlatform;
  condition: GameCondition;
  notes: string | null;
  distanceKm: number | null;
  listedAt: string;
}

export interface GameReview {
  id: string;
  gameId: string;
  userId: string;
  userDisplayName: string;
  userAvatarSeed: string;
  userLocalityName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface GameDiscussion {
  id: string;
  gameId: string;
  userId: string;
  userDisplayName: string;
  userAvatarSeed: string;
  userLocalityName: string;
  body: string;
  createdAt: string;
}

export interface HaveListing {
  id: string;
  userId: string;
  gameId: string;
  gameTitle: string;
  gameCoverUrl: string | null;
  platform: ConsolePlatform;
  condition: GameCondition;
  notes: string | null;
  status: string;
  createdAt: string;
}

export interface WantListing {
  id: string;
  userId: string;
  gameId: string;
  gameTitle: string;
  gameCoverUrl: string | null;
  platform: ConsolePlatform;
  status: string;
  createdAt: string;
}

export interface MatchedGameItem {
  listingId: string;
  gameId: string;
  title: string;
  coverUrl: string | null;
  platform: ConsolePlatform;
  condition: GameCondition;
}

export interface MatchCard {
  userId: string;
  displayName: string;
  avatarSeed: string;
  localityName: string;
  consoles: ConsolePlatform[];
  ratingAvg: number;
  ratingCount: number;
  completedSwapsCount: number;
  matchTier: number;
  matchType: "TWO_WAY" | "ONE_WAY";
  distanceKm: number;
  distanceMeters: number;
  theyHaveGamesIWant: MatchedGameItem[];
  theyWantGamesIHave: MatchedGameItem[];
  otherHaveListings: MatchedGameItem[];
}

export interface MapPin {
  listingId: string;
  userId: string;
  userDisplayName: string;
  userAvatarSeed: string;
  localityName: string;
  userRatingAvg: number;
  gameId: string;
  gameTitle: string;
  gameCoverUrl: string | null;
  platform: ConsolePlatform;
  condition: GameCondition;
  fuzzedLatitude: number;
  fuzzedLongitude: number;
  distanceKm: number;
  matchTier: number;
  listedAt: string;
}

export interface ChatThread {
  id: string;
  otherUserId: string;
  otherUserDisplayName: string;
  otherUserAvatarSeed: string;
  otherUserLocalityName: string;
  otherUserRatingAvg: number;
  lastMessagePreview: string | null;
  lastMessageAt: string;
  createdAt: string;
}

export interface SwapListingSummary {
  listingId: string;
  ownerId: string;
  ownerDisplayName: string;
  gameId: string;
  gameTitle: string;
  gameCoverUrl: string | null;
  platform: ConsolePlatform;
  condition: GameCondition;
}

export interface Swap {
  id: string;
  threadId: string;
  proposerId: string;
  proposerDisplayName: string;
  recipientId: string;
  recipientDisplayName: string;
  proposerListing: SwapListingSummary;
  recipientListing: SwapListingSummary;
  meetupSpotId: string | null;
  meetupSpotName: string | null;
  meetupSpotAddress: string | null;
  meetupTime: string | null;
  status: "PROPOSED" | "ACCEPTED" | "COMPLETED" | "DECLINED" | "CANCELLED";
  proposerConfirmed: boolean;
  recipientConfirmed: boolean;
  myRatingSubmitted: boolean;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  threadId: string;
  senderId: string;
  senderDisplayName: string;
  senderAvatarSeed: string;
  messageType: "TEXT" | "SWAP_PROPOSAL" | "MEETUP_SUGGESTION" | "SYSTEM";
  body: string;
  swapId: string | null;
  swap: Swap | null;
  meetupSpotId: string | null;
  meetupSpotName: string | null;
  meetupSpotAddress: string | null;
  createdAt: string;
}

export interface UserRating {
  id: string;
  swapId: string;
  raterId: string;
  raterDisplayName: string;
  raterAvatarSeed: string;
  raterLocalityName: string;
  rateeId: string;
  stars: number;
  comment: string | null;
  createdAt: string;
}

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  body: string;
  referenceId: string | null;
  isRead: boolean;
  createdAt: string;
}

