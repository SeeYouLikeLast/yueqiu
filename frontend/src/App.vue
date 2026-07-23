<script setup lang="ts">
// 新手阅读地图：本文件采用 Vue <script setup>，从上到下依次是类型定义、响应式状态、
// 数据加载函数、用户操作函数和生命周期；文件末尾依次是 template 与 scoped style。
// 阅读某个页面时不要从头硬啃：先在 template 搜索页面标题，再从 @click/数据变量反查同名函数，
// 最后沿 api('/...') 对照后端 Controller -> Service -> Mapper 阅读完整链路。
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Activity,
  Bot,
  Camera,
  ChevronLeft,
  ClipboardList,
  CreditCard,
  Eye,
  EyeOff,
  ExternalLink,
  Heart,
  ImagePlus,
  KeyRound,
  LocateFixed,
  LogIn,
  Mail,
  MapPin,
  MessageCircle,
  PenLine,
  Phone,
  Search,
  Send,
  ShieldCheck,
  ShoppingBag,
  ShoppingCart,
  Trash2,
  UserRound,
  Users,
  X,
  Zap
} from 'lucide-vue-next'
import { api, clearToken, getToken, PageResult, setToken, streamApi } from './api/client'
import { locateWithAmapFirst, type PreciseLocation } from './api/amapGeolocation'

type Tab = 'home' | 'seckill' | 'social' | 'equipment' | 'profile' | 'assistant'
type VenueSaleType = '' | 'TIME_PACKAGE' | 'COURT_SLOT' | 'COACH_LESSON'

type SportType = {
  code: string
  name: string
  keywords: string[]
}

type Place = {
  id: string
  name: string
  sportCode: string
  sportName: string
  city: string
  area: string
  address: string
  longitude?: number
  latitude?: number
  distanceMeters?: number
  type?: string
  tel?: string
  businessArea?: string
  openHours?: string
  coverUrl?: string
  facilities: string[]
  source: string
}

type VenueItem = {
  id: number
  venueId?: number
  amapPlaceId?: string
  venueName: string
  placeRank?: number
  sportCode: string
  productType: 'TIME_PACKAGE' | 'COURT_SLOT' | 'COACH_LESSON'
  productTypeName: string
  title: string
  description: string
  coverUrl?: string
  price: number
  originalPrice?: number
  tags: string[]
  useRule: string
  refundRule: string
  purchasable: boolean
}

type VenueInventory = {
  id: number
  productId: number
  courtName?: string
  coachId?: number
  coachName?: string
  serviceDate: string
  startTime: string
  endTime: string
  price: number
  purchasable: boolean
}

type VenueOrder = {
  id: number
  venueName: string
  productTitle: string
  productType: string
  serviceDate: string
  startTime: string
  endTime: string
  amount: number
  status: string
  verifyCode: string
}

type CartItem = {
  id: number
  type: number
  productId: number
  inventoryId?: number
  productName: string
  brand: string
  coverUrl: string
  price: number
  quantity: number
  amount: number
  meta: string
}

type EquipmentItem = {
  id: number
  sportCode: string
  categoryId: number
  categoryName: string
  name: string
  brand: string
  description: string
  coverUrl: string
  price: number
  score: number
  sold: number
}

type Category = {
  id: number
  sportCode: string
  name: string
  icon: string
}

type SeckillActivity = {
  id: number
  productName: string
  sportCode: string
  categoryId: number
  categoryName: string
  coverUrl: string
  originalPrice: number
  seckillPrice: number
  purchasable: boolean
}

type Player = {
  userId: number
  nickname: string
  avatar: string
  sportCode: string
  city: string
  area: string
  level: string
  playStyle: string
  availableTime: string
  intro: string
  allowInvite?: boolean
  longitude?: number
  latitude?: number
  distanceMeters?: number
}

type SportActivity = {
  id: number
  creatorId: number
  creatorName: string
  sportCode: string
  venueName: string
  title: string
  city: string
  startTime: string
  endTime: string
  maxPlayers: number
  currentPlayers: number
  levelRequired: string
  feeType: string
}

type VenueReview = {
  id: number
  venueId: number
  userId: number
  nickname: string
  avatar: string
  rating: number
  content: string
  imageUrls: string
  likes: number
  createdAt: string
}

type UserProfile = {
  id: number
  phone?: string
  email?: string
  username?: string
  nickname: string
  avatar?: string
  city: string
  level: string
  prefer_time?: string
  created_at?: string
}

type FileMetadata = {
  id: number
  publicUrl: string
  originalFilename: string
  contentType: string
  fileSize: number
}

type UserPublicProfile = {
  id: number
  nickname: string
  avatar?: string
  city?: string
  level?: string
  preferTime?: string
  createdAt?: string
  followed: boolean
  isMe: boolean
}

type LoginCodeResponse = {
  email: string
  expireSeconds: number
  cooldownSeconds: number
}

type RegeoLocation = {
  city?: string
  district?: string
  formattedAddress?: string
  shortAddress?: string
}

type IpLocation = RegeoLocation & {
  lng?: number
  lat?: number
  accuracy?: number
  source?: 'ip' | 'default'
}

type BlogChannel = 'follow' | 'recommend' | 'sport'
type ProfileMode = 'me' | 'public'
type ActivityScope = 'created' | 'others' | 'joined'

type ProfileOrderCard = {
  key: string
  title: string
  subtitle: string
  meta: string
  amount: number
  status: string
  code: string
}
type BlogPost = {
  id: number
  userId: number
  nickname: string
  avatar?: string
  sportCode: string
  title: string
  content: string
  images: string[]
  relatedType: 'EQUIPMENT' | 'VENUE_PRODUCT'
  relatedId: number
  relatedTitle: string
  relatedCoverUrl?: string
  relatedPrice?: number
  liked: number
  isLiked: boolean
  followed: boolean
  createdAt: string
}

type BlogRelatedOption = {
  key: string
  type: BlogPost['relatedType']
  id: number
  title: string
  coverUrl?: string
  price?: number
  sportCode: string
  subtitle: string
}

type AgentAction = {
  type: string
  id?: string
  requireConfirm?: boolean
  payload?: Record<string, unknown>
}

type AgentCard = {
  cardId?: string
  type: 'place' | 'venue_product' | 'activity' | 'equipment' | 'seckill'
  title: string
  subtitle?: string
  coverUrl?: string
  price?: string
  tags?: string[]
  action?: AgentAction
  meta?: Record<string, unknown>
}

type AgentMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  cards?: AgentCard[]
}

type AgentConversation = {
  id: number
  title: string
  createdAt?: string
  updatedAt?: string
}

type AgentMessageRecord = {
  id: number
  role: string
  content: string
  cards?: AgentCard[]
  createdAt?: string
}

type AgentChatResponse = {
  conversationId: number
  answer: string
  cards: AgentCard[]
  quickReplies: string[]
  aiEnabled: boolean
}

type AgentStatus = {
  aiEnabled: boolean
}

type AgentPlaceBundle = {
  place: AgentCard
  deal?: AgentCard
}

type AgentVenueBooking = {
  product: AgentCard
  place?: AgentCard
  inventories: VenueInventory[]
  inventoryId: number
  maxPlayers: number
  levelRequired: string
}

type ScrollResult<T> = {
  list: T[]
  minTime?: number
  offset?: number
}

const fallbackSports: SportType[] = [
  { code: 'badminton', name: '羽毛球', keywords: ['羽毛球馆'] },
  { code: 'table_tennis', name: '乒乓球', keywords: ['乒乓球馆'] },
  { code: 'football', name: '足球', keywords: ['足球场'] },
  { code: 'basketball', name: '篮球', keywords: ['篮球场'] },
  { code: 'tennis', name: '网球', keywords: ['网球场'] },
  { code: 'volleyball', name: '排球', keywords: ['排球馆'] }
]

const ALL_SPORT: SportType = { code: '', name: '全部运动', keywords: [] }
const DEFAULT_PLACE_RADIUS = 5000
// 首屏只展示手机一屏附近场所，继续上滑再按页加载，避免首次高德搜索和图片渲染过重。
const PLACE_PAGE_SIZE = 10
const PRODUCT_PAGE_SIZE = 12
const SOCIAL_PAGE_SIZE = 12
const BLOG_PAGE_SIZE = 10
const VENUE_TEMPLATE_COUNT = 2
const FALLBACK_IMAGE = '/objects/hm-badminton/demo/places/cover/place-031.png'
const FALLBACK_AVATAR = '/objects/hm-badminton/demo/users/avatar/avatar-001.png'
const HEADER_SCROLL_DELTA = 8
const HEADER_HIDE_AFTER = 190
const HEADER_SHOW_ACCUMULATE = 10
const HEADER_HIDE_ACCUMULATE = 20
const PLAYER_COUNT_OPTIONS = [2, 3, 4, 5, 6, 8, 10, 12, 16, 20]
const AGENT_WELCOME_MESSAGE = '你好，我是约个球助手。可以帮你找附近场所、可加入的约球活动，也能按预算推荐装备。'
const INITIAL_AGENT_QUICK_REPLIES = ['今晚附近能打球吗', '帮我找能加入的局', '推荐新手装备', '50 元以内的场地']
const FALLBACK_AGENT_FOLLOW_UPS = ['再给我 3 个选择', '帮我按距离筛选', '帮我按预算筛选', '这些哪个更适合新手']
const activeTab = ref<Tab>('home')
const homeReturnTab = ref<Tab | null>(null)
const selectedSport = ref('')
type ProfileView = 'orders' | 'paid' | 'cart'
const authToken = ref(getToken())
const loading = ref(false)
const locating = ref(false)
const locationAccuracy = ref<number | null>(null)
const message = ref('')
const authPageVisible = ref(false)
const authReturnTab = ref<Tab>('home')
const sports = ref<SportType[]>(fallbackSports)
const places = ref<Place[]>([])
const selectedPlace = ref<Place | null>(null)
const placeDetailTab = ref<'deals' | 'reviews'>('deals')
const dealFilter = ref<'all' | 'discount'>('all')
const venueSaleView = ref<{ productType: VenueSaleType; title: string; subtitle: string } | null>(null)
const venueItems = ref<Record<string, VenueItem[]>>({})
const venueSaleItems = ref<VenueItem[]>([])
const venueReviews = ref<VenueReview[]>([])
const inventoriesByVenueItem = ref<Record<number, VenueInventory[]>>({})
const highlightedItemKey = ref('')
const categories = ref<Category[]>([])
const seckillCategories = ref<Category[]>([])
const productCategorySport = ref('')
const seckillCategorySport = ref('')
const products = ref<EquipmentItem[]>([])
const seckillActivities = ref<SeckillActivity[]>([])
const blogs = ref<BlogPost[]>([])
const blogChannel = ref<BlogChannel>('recommend')
const blogSport = ref('')
const blogKeyword = ref('')
const players = ref<Player[]>([])
const activities = ref<SportActivity[]>([])
const activityScope = ref<ActivityScope>('others')
const venueOrders = ref<ProfileOrderCard[]>([])
const equipmentOrders = ref<ProfileOrderCard[]>([])
const cartItems = ref<CartItem[]>([])
const userProfile = ref<UserProfile | null>(null)
const socialProfile = ref<Player | null>(null)
const viewedUserProfile = ref<UserPublicProfile | null>(null)
const profileBlogs = ref<BlogPost[]>([])
const selectedBlog = ref<BlogPost | null>(null)
const relatedBlogReturn = ref<BlogPost | null>(null)
const agentMessages = ref<AgentMessage[]>(defaultAgentMessages())
const agentInput = ref('')
const agentInputPlaceholder = ref('问问附近场地、约球活动、装备推荐')
const agentConversationId = ref<number | null>(null)
const agentConversations = ref<AgentConversation[]>([])
const agentQuickReplies = ref<string[]>(initialAgentQuickReplies())
const agentSportPickerVisible = ref(false)
const agentPendingQuickReply = ref('')
const agentSelectedSportCodes = ref<string[]>([])
const agentAiEnabled = ref(false)
const agentThinking = ref(false)
const agentProgressText = ref('')
const agentHistoryLoaded = ref(false)
const agentHistoryVisible = ref(false)
const deletingAgentConversation = ref<AgentConversation | null>(null)
const loadingAgentHistory = ref(false)
const agentVenueBooking = ref<AgentVenueBooking | null>(null)
const agentVenueBookingSubmitting = ref(false)
const blogComposerVisible = ref(false)
const blogComposerReturnTab = ref<Tab>('seckill')
const cartCount = ref(0)
const cartLoaded = ref(false)
const ordersLoaded = ref(false)
const codeCountdown = ref(0)
const buyingVenueItemId = ref<number | null>(null)
const purchaseNotice = ref('')
const profileView = ref<ProfileView>('orders')
const profileMode = ref<ProfileMode>('me')
const publicProfileReturnTab = ref<Tab>('seckill')
const profileOrderPageVisible = ref(false)
const profileEditorVisible = ref(false)
const loadingMore = ref(false)
const placesPage = ref(1)
const placesTotal = ref(0)
const productsPage = ref(1)
const productsTotal = ref(0)
const venueSalesPage = ref(1)
const venueSalesTotal = ref(0)
const playersPage = ref(1)
const playersTotal = ref(0)
const activitiesPage = ref(1)
const activitiesTotal = ref(0)
const blogsPage = ref(1)
const blogsTotal = ref(0)
const followFeedLastId = ref<number | undefined>()
const followFeedOffset = ref(0)
const followFeedHasMore = ref(true)
// 推荐流首屏会在首页空闲时预取；登录态改变后自动失效，避免复用游客的点赞/关注状态。
let recommendedBlogsLoadedToken: string | null | undefined
let recommendedBlogsRequest: Promise<void> | null = null
let recommendedBlogsRequestToken: string | null | undefined
const profileBlogsPage = ref(1)
const profileBlogsTotal = ref(0)
const headerVisible = ref(true)
let lastHeaderScrollTop = 0
let headerScrollUpDistance = 0
let headerScrollDownDistance = 0

const loggedIn = computed(() => Boolean(authToken.value))
const activeSport = computed(() => sports.value.find((sport) => sport.code === selectedSport.value) || ALL_SPORT)
const cartAmount = computed(() => cartItems.value.reduce((sum, item) => sum + Number(item.amount), 0))
const showPhoneHeader = computed(() => !blogComposerVisible.value && !(activeTab.value === 'home' && selectedPlace.value))
const showDiscoveryHeader = computed(() => !blogComposerVisible.value && activeTab.value !== 'profile' && activeTab.value !== 'assistant')
const showCategoryHeader = computed(() => activeTab.value === 'equipment' && Boolean(selectedSport.value))
const showAgentFab = computed(() => activeTab.value !== 'assistant')
const assistantFabPrompt = computed(() => {
  if (activeTab.value === 'home' && selectedPlace.value) {
    return `帮我分析一下${selectedPlace.value.name}适合买什么团购，或者怎么约球`
  }
  if (activeTab.value === 'home' && venueSaleView.value) {
    return '帮我从这些场馆团购里选一个性价比高的'
  }
  if (activeTab.value === 'social') {
    return '帮我看看有没有适合加入的约球活动'
  }
  if (activeTab.value === 'equipment') {
    return '帮我按预算和运动类型推荐装备'
  }
  if (activeTab.value === 'seckill') {
    return '帮我看看社区里有哪些值得参考的装备或场馆体验'
  }
  if (activeTab.value === 'profile') {
    return '帮我看看我的订单、购物车或运动偏好'
  }
  return '帮我看看附近有什么适合的场地或约球活动'
})
const hasAgentConversation = computed(() => agentMessages.value.some((item) => item.role === 'user'))
const agentBookingInventory = computed(() => agentVenueBooking.value?.inventories
  .find((item) => item.id === agentVenueBooking.value?.inventoryId) || null)
const visibleAgentQuickReplies = computed(() => agentThinking.value || agentSportPickerVisible.value ? [] : agentQuickReplies.value.slice(0, 4))
const hasMorePlaces = computed(() => places.value.length < placesTotal.value)
const hasMoreVenueSales = computed(() => venueSaleItems.value.length < venueSalesTotal.value)
const hasMoreEquipmentItems = computed(() => products.value.length < productsTotal.value)
const hasMoreSocial = computed(() => players.value.length < playersTotal.value || activities.value.length < activitiesTotal.value)
const hasMoreBlogs = computed(() => blogChannel.value === 'follow' ? followFeedHasMore.value : blogs.value.length < blogsTotal.value)
const hasMoreProfileBlogs = computed(() => profileBlogs.value.length < profileBlogsTotal.value)
const profileTitle = computed(() => profileMode.value === 'public' ? 'TA 的主页' : '我的')
const profileSubtitle = computed(() => profileMode.value === 'public' ? '球友主页' : '个人中心')
const profileAvatar = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.avatar : userProfile.value?.avatar)
const profileNickname = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.nickname : userProfile.value?.nickname)
const profileCity = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.city : socialProfile.value?.city || userProfile.value?.city)
const profileLevel = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.level : socialProfile.value?.level || userProfile.value?.level)
const profilePreferTime = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.preferTime : socialProfile.value?.availableTime || userProfile.value?.prefer_time)
const profileCreatedAt = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.createdAt : userProfile.value?.created_at)
const paidStatus = (status: string) => ['已支付', '已抢到', '已使用'].includes(status)
const visibleVenueOrders = computed(() => profileView.value === 'paid'
  ? venueOrders.value.filter((order) => paidStatus(order.status))
  : venueOrders.value)
const visibleEquipmentOrders = computed(() => profileView.value === 'paid'
  ? equipmentOrders.value.filter((order) => paidStatus(order.status))
  : equipmentOrders.value)
const profileVenueOrderCards = computed<ProfileOrderCard[]>(() => visibleVenueOrders.value)
const profileEquipmentOrderCards = computed<ProfileOrderCard[]>(() => visibleEquipmentOrders.value)
const selectedPlaceVenueItems = computed(() => {
  if (!selectedPlace.value) return []
  const records = saleVenueItems(selectedPlace.value)
  if (dealFilter.value === 'discount') {
    return records.filter((item) => item.originalPrice && Number(item.originalPrice) > Number(item.price))
  }
  return records
})
const filteredSeckillActivities = computed(() => {
  const keyword = productQuery.keyword.trim().toLowerCase()
  const records = productQuery.categoryId
    ? seckillActivities.value.filter((activity) => String(activity.categoryId) === productQuery.categoryId)
    : seckillActivities.value
  if (!keyword) return records
  return records.filter((activity) =>
    [activity.productName, activity.categoryName].some((value) => value.toLowerCase().includes(keyword))
  )
})
const headerSearchKeyword = computed({
  get() {
    if (activeTab.value === 'equipment') return productQuery.keyword
    if (activeTab.value === 'seckill') return blogKeyword.value
    return placeQuery.keyword
  },
  set(value: string) {
    if (activeTab.value === 'equipment') {
      productQuery.keyword = value
      return
    }
    if (activeTab.value === 'seckill') {
      blogKeyword.value = value
      return
    }
    placeQuery.keyword = value
  }
})
const headerSearchPlaceholder = computed(() => {
  if (activeTab.value === 'equipment') return '搜装备、品牌、分类'
  if (activeTab.value === 'seckill') return '搜动态、装备心得、场馆体验'
  if (activeTab.value === 'social') return '搜球友、活动、场所'
  return '搜球馆、球场、私教课'
})

const bottomTabs = [
  { key: 'home' as const, label: '首页', icon: MapPin },
  { key: 'seckill' as const, label: '社区', icon: Heart },
  { key: 'social' as const, label: '约球', icon: Users },
  { key: 'equipment' as const, label: '装备', icon: ShoppingBag },
  { key: 'profile' as const, label: '我的', icon: UserRound },
  { key: 'assistant' as const, label: '助手', icon: Bot }
]

const loginForm = reactive({
  email: '',
  code: ''
})

const passwordLoginForm = reactive({
  account: '',
  password: ''
})

const authMode = ref<'code' | 'password'>('code')
const passwordVisible = ref(false)

const placeQuery = reactive({
  city: '西安',
  preciseAddress: '',
  keyword: '',
  lng: 108.946465,
  lat: 34.347269,
  radius: 5000
})

const productQuery = reactive({
  categoryId: '',
  keyword: ''
})

const seckillQuery = reactive({
  categoryId: '',
  keyword: ''
})

function padDatePart(value: number) {
  return String(value).padStart(2, '0')
}

function toDatetimeLocal(value: Date) {
  return [
    value.getFullYear(),
    padDatePart(value.getMonth() + 1),
    padDatePart(value.getDate())
  ].join('-') + `T${padDatePart(value.getHours())}:${padDatePart(value.getMinutes())}`
}

function nextActivityWindow() {
  const start = new Date()
  start.setHours(19, 0, 0, 0)
  if (start <= new Date()) {
    start.setDate(start.getDate() + 1)
  }
  const end = new Date(start)
  end.setHours(21, 0, 0, 0)
  return {
    startTime: toDatetimeLocal(start),
    endTime: toDatetimeLocal(end)
  }
}

const defaultActivityWindow = nextActivityWindow()

const activityForm = reactive({
  sportCode: 'badminton',
  placeSource: 'amap',
  placeId: '',
  venueName: '先从场所列表选择',
  title: '今晚约一场',
  city: '西安',
  startTime: defaultActivityWindow.startTime,
  endTime: defaultActivityWindow.endTime,
  maxPlayers: 4,
  levelRequired: '不限',
  feeType: 'AA'
})

const profileForm = reactive({
  phone: '',
  username: '',
  email: '',
  password: '',
  nickname: '',
  avatar: '',
  sportCode: 'badminton',
  city: '西安',
  area: '未央区',
  longitude: 108.946465,
  latitude: 34.347269,
  level: '中级',
  playStyle: '双打',
  availableTime: '周末下午',
  intro: '想找固定球友，工作日晚上和周末都可以约。',
  allowInvite: true
})

const blogPublishForm = reactive({
  sportCode: 'badminton',
  title: '',
  content: '',
  imageUrls: [] as string[],
  relatedKey: ''
})

const blogRelatedOptions = computed<BlogRelatedOption[]>(() => {
  const records: BlogRelatedOption[] = []
  const seen = new Set<string>()
  const push = (option: BlogRelatedOption) => {
    if (seen.has(option.key)) return
    seen.add(option.key)
    records.push(option)
  }
  products.value.forEach((item) => {
    push({
      key: `EQUIPMENT:${item.id}`,
      type: 'EQUIPMENT',
      id: item.id,
      title: item.name,
      coverUrl: item.coverUrl,
      price: item.price,
      sportCode: item.sportCode,
      subtitle: `${item.brand} · ${item.categoryName}`
    })
  })
  const venueProducts = [
    ...venueSaleItems.value,
    ...Object.values(venueItems.value).flat(),
    ...selectedPlaceVenueItems.value
  ]
  venueProducts.forEach((item) => {
    push({
      key: `VENUE_PRODUCT:${item.id}`,
      type: 'VENUE_PRODUCT',
      id: item.id,
      title: item.title,
      coverUrl: item.coverUrl,
      price: item.price,
      sportCode: item.sportCode,
      subtitle: `${item.venueName} · ${item.productTypeName}`
    })
  })
  return records.filter((item) => !blogPublishForm.sportCode || item.sportCode === blogPublishForm.sportCode)
})

const locationLabel = computed(() => placeQuery.city || '当前位置')
const preciseLocationLabel = computed(() => placeQuery.preciseAddress || `${placeQuery.lng.toFixed(4)}, ${placeQuery.lat.toFixed(4)}`)

let messageTimer: ReturnType<typeof window.setTimeout> | undefined
let codeTimer: ReturnType<typeof window.setInterval> | undefined

watch([activeTab, selectedPlace, venueSaleView, blogComposerVisible], () => {
  resetHeaderVisibility()
})

watch(message, (value) => {
  if (messageTimer) {
    window.clearTimeout(messageTimer)
    messageTimer = undefined
  }
  if (!value) return
  messageTimer = window.setTimeout(() => {
    message.value = ''
    messageTimer = undefined
  }, 3000)
})

function closeMessage() {
  if (messageTimer) {
    window.clearTimeout(messageTimer)
    messageTimer = undefined
  }
  message.value = ''
}

function imageFallback(event: Event, fallback = FALLBACK_IMAGE) {
  const image = event.target as HTMLImageElement
  if (!image || image.src.endsWith(fallback)) return
  image.src = fallback
}

function resetListPaging() {
  placeQuery.radius = DEFAULT_PLACE_RADIUS
  placesPage.value = 1
  placesTotal.value = 0
  venueSalesPage.value = 1
  venueSalesTotal.value = 0
  productsPage.value = 1
  productsTotal.value = 0
  playersPage.value = 1
  playersTotal.value = 0
  activitiesPage.value = 1
  activitiesTotal.value = 0
  blogsPage.value = 1
  blogsTotal.value = 0
  followFeedLastId.value = undefined
  followFeedOffset.value = 0
  followFeedHasMore.value = true
  profileBlogsPage.value = 1
  profileBlogsTotal.value = 0
}

function startCodeCountdown(seconds: number) {
  if (codeTimer) {
    window.clearInterval(codeTimer)
    codeTimer = undefined
  }
  codeCountdown.value = seconds
  codeTimer = window.setInterval(() => {
    codeCountdown.value -= 1
    if (codeCountdown.value <= 0 && codeTimer) {
      window.clearInterval(codeTimer)
      codeTimer = undefined
    }
  }, 1000)
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败'
}

function resetLoginState() {
  clearToken()
  authToken.value = ''
  userProfile.value = null
  socialProfile.value = null
  venueOrders.value = []
  equipmentOrders.value = []
  cartItems.value = []
  profileBlogs.value = []
  viewedUserProfile.value = null
  selectedBlog.value = null
  blogComposerVisible.value = false
  cartCount.value = 0
  cartLoaded.value = false
  ordersLoaded.value = false
  profileView.value = 'orders'
  profileMode.value = 'me'
  publicProfileReturnTab.value = 'seckill'
  profileOrderPageVisible.value = false
  profileEditorVisible.value = false
  resetAgentChat()
}

function handleRequestError(error: unknown) {
  const text = errorMessage(error)
  if (text.includes('请先登录')) {
    resetLoginState()
  }
  return text
}

function defaultAgentMessages(): AgentMessage[] {
  // A new guest starts blank; once a guest has chatted, history is restored from its HttpOnly-cookie Redis session.
  if (!authToken.value) return []
  return [
    {
      id: 'welcome',
      role: 'assistant',
      content: AGENT_WELCOME_MESSAGE
    }
  ]
}

function initialAgentQuickReplies() {
  return [...INITIAL_AGENT_QUICK_REPLIES]
}

function resetAgentChat() {
  agentMessages.value = defaultAgentMessages()
  agentConversationId.value = null
  agentConversations.value = []
  agentQuickReplies.value = initialAgentQuickReplies()
  agentSportPickerVisible.value = false
  agentPendingQuickReply.value = ''
  agentSelectedSportCodes.value = []
  agentThinking.value = false
  agentProgressText.value = ''
  agentHistoryLoaded.value = false
  agentHistoryVisible.value = false
  deletingAgentConversation.value = null
  loadingAgentHistory.value = false
}

function toAgentMessage(record: AgentMessageRecord): AgentMessage | null {
  const role = record.role === 'user' ? 'user' : record.role === 'assistant' ? 'assistant' : null
  if (!role || !record.content) return null
  return {
    id: `m-${record.id}`,
    role,
    content: record.content,
    cards: record.cards || []
  }
}

async function loadLatestAgentHistory() {
  if (agentHistoryLoaded.value) return
  try {
    const conversations = await api<AgentConversation[]>('/agent/conversations')
    agentConversations.value = conversations
    const latest = conversations[0]
    if (!latest) {
      agentHistoryLoaded.value = true
      return
    }
    const records = await api<AgentMessageRecord[]>(`/agent/conversations/${latest.id}/messages`)
    const restored = records.map(toAgentMessage).filter(Boolean) as AgentMessage[]
    agentConversationId.value = latest.id
    agentMessages.value = restored.length ? restored : defaultAgentMessages()
    syncAgentQuickRepliesFromMessages()
  } catch (error) {
    message.value = handleRequestError(error)
  } finally {
    agentHistoryLoaded.value = true
  }
}

async function loadAgentConversations() {
  loadingAgentHistory.value = true
  try {
    agentConversations.value = await api<AgentConversation[]>('/agent/conversations')
  } catch (error) {
    message.value = handleRequestError(error)
  } finally {
    loadingAgentHistory.value = false
  }
}

async function loadAgentStatus() {
  try {
    const status = await api<AgentStatus>('/agent/status')
    agentAiEnabled.value = status.aiEnabled
  } catch {
    agentAiEnabled.value = false
  }
}

async function openAgentHistory() {
  agentHistoryVisible.value = true
  deletingAgentConversation.value = null
  await loadAgentConversations()
}

function closeAgentHistory() {
  agentHistoryVisible.value = false
  deletingAgentConversation.value = null
}

async function switchAgentConversation(conversation: AgentConversation) {
  if (conversation.id === agentConversationId.value) {
    closeAgentHistory()
    return
  }
  loadingAgentHistory.value = true
  try {
    const records = await api<AgentMessageRecord[]>(`/agent/conversations/${conversation.id}/messages`)
    const restored = records.map(toAgentMessage).filter(Boolean) as AgentMessage[]
    agentConversationId.value = conversation.id
    agentMessages.value = restored.length ? restored : defaultAgentMessages()
    syncAgentQuickRepliesFromMessages()
    closeAgentHistory()
    await positionAgentOnEntry()
  } catch (error) {
    message.value = handleRequestError(error)
  } finally {
    loadingAgentHistory.value = false
  }
}

function startNewAgentConversation() {
  closeAgentHistory()
  agentConversationId.value = null
  agentMessages.value = defaultAgentMessages()
  agentQuickReplies.value = initialAgentQuickReplies()
  agentSportPickerVisible.value = false
  agentPendingQuickReply.value = ''
  agentSelectedSportCodes.value = []
  agentThinking.value = false
  agentProgressText.value = ''
  agentInput.value = ''
  scrollToPageTop()
}

function askDeleteAgentConversation(conversation: AgentConversation) {
  deletingAgentConversation.value = conversation
}

function deleteAgentConversation(conversation: AgentConversation) {
  askDeleteAgentConversation(conversation)
}

function cancelDeleteAgentConversation() {
  deletingAgentConversation.value = null
}

async function confirmDeleteAgentConversation() {
  if (!deletingAgentConversation.value) return
  const conversation = deletingAgentConversation.value
  loadingAgentHistory.value = true
  try {
    await api(`/agent/conversations/${conversation.id}`, { method: 'DELETE' })
    agentConversations.value = agentConversations.value.filter((item) => item.id !== conversation.id)
    deletingAgentConversation.value = null
    if (conversation.id === agentConversationId.value) {
      startNewAgentConversation()
    }
  } catch (error) {
    message.value = handleRequestError(error)
  } finally {
    loadingAgentHistory.value = false
  }
}

function agentConversationTitle(conversation: AgentConversation) {
  return conversation.title?.trim() || '新的约球咨询'
}

function agentConversationTime(conversation: AgentConversation) {
  const value = conversation.updatedAt || conversation.createdAt
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(5, 16)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function normalizeQuickReplies(replies?: string[]) {
  const unique: string[] = []
  for (const reply of replies || []) {
    const text = reply?.trim()
    if (text && !unique.includes(text)) {
      unique.push(text)
    }
  }
  return unique.slice(0, 4)
}

function syncAgentQuickRepliesFromMessages() {
  for (let i = agentMessages.value.length - 1; i >= 0; i--) {
    const item = agentMessages.value[i]
    if (item.role === 'assistant' && item.id !== 'welcome') {
      agentQuickReplies.value = buildAgentFollowUps(item.cards || [], item.content)
      return
    }
  }
  agentQuickReplies.value = initialAgentQuickReplies()
}

function agentNextQuickReplies(replies: string[] | undefined, cards: AgentCard[] = [], answer = '') {
  const normalized = normalizeQuickReplies(replies)
  return normalized.length ? normalized : buildAgentFollowUps(cards, answer)
}

function buildAgentFollowUps(cards: AgentCard[] = [], answer = '') {
  const types = new Set(cards.map((card) => card.type))
  const replies: string[] = []
  if (types.has('place')) {
    replies.push('看看这些场所的团购', '帮我按距离重新筛')
  }
  if (types.has('venue_product')) {
    replies.push('哪一个团购最划算', '帮我看预约规则')
  }
  if (types.has('activity')) {
    replies.push('哪些局现在能加入', '帮我选适合新手的局')
  }
  if (types.has('equipment') || types.has('seckill')) {
    replies.push('帮我按预算筛装备', '这几件适合新手吗')
  }
  if (answer.includes('预算') || answer.includes('价格')) {
    replies.push('帮我换成更便宜的')
  }
  if (answer.includes('附近') || answer.includes('距离')) {
    replies.push('换成离我更近的')
  }
  return normalizeQuickReplies(replies.length ? replies : FALLBACK_AGENT_FOLLOW_UPS)
}

async function wrap(action: () => Promise<void>, okMessage?: string) {
  loading.value = true
  message.value = ''
  try {
    await action()
    if (okMessage) message.value = okMessage
  } catch (error) {
    message.value = handleRequestError(error)
  } finally {
    loading.value = false
  }
}

async function sendLoginCode() {
  await wrap(async () => {
    const result = await api<LoginCodeResponse>('/auth/code', {
      method: 'POST',
      body: JSON.stringify({ email: loginForm.email.trim() })
    })
    startCodeCountdown(result.cooldownSeconds || 60)
    message.value = `验证码已发送至 ${result.email}，5 分钟内有效`
  })
}

async function login() {
  await wrap(async () => {
    const body = authMode.value === 'password'
      ? {
          account: passwordLoginForm.account.trim(),
          password: passwordLoginForm.password
        }
      : {
          email: loginForm.email.trim(),
          code: loginForm.code.trim()
        }
    const result = await api<{ token: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(body)
    })
    setToken(result.token)
    authToken.value = result.token
    resetAgentChat()
    activeTab.value = authReturnTab.value
    authPageVisible.value = false
    const located = await refreshCurrentLocation({ reload: false, showMessage: false })
    await loadCurrentTab()
    message.value = located ? '登录成功，已刷新当前位置' : '登录成功，未获取当前位置'
  })
}

async function loadSports() {
  sports.value = await api<SportType[]>('/sports')
}

function appendByKey<T>(current: T[], next: T[], keyOf: (item: T) => string | number) {
  const seen = new Set(current.map(keyOf))
  return [...current, ...next.filter((item) => !seen.has(keyOf(item)))]
}

function appendById<T extends { id: string | number }>(current: T[], next: T[]) {
  return appendByKey(current, next, (item) => item.id)
}

function placesForSport(sportCode?: string) {
  if (!sportCode) return places.value
  const records = places.value.filter((item) => item.sportCode === sportCode)
  return records.length ? records : places.value
}

function placeIndex(place: Place) {
  const records = placesForSport(place.sportCode)
  const index = records.findIndex((item) => item.id === place.id)
  return index >= 0 ? index : places.value.findIndex((item) => item.id === place.id)
}

function placeRankFor(place: Place) {
  const index = Math.max(0, placeIndex(place))
  return (index % VENUE_TEMPLATE_COUNT) + 1
}

function placeForEquipmentItem(product: VenueItem) {
  if (!places.value.length) return null
  const rank = product.placeRank || Number(product.venueName) || 1
  const candidates = placesForSport(product.sportCode)
  return candidates.find((place) => placeRankFor(place) === rank)
    || candidates[(Math.max(1, rank) - 1) % candidates.length]
    || null
}

function productWithPlaceContext(product: VenueItem, place?: Place | null) {
  if (!place) return product
  return {
    ...product,
    amapPlaceId: place.id,
    venueName: place.name
  }
}

function itemDomId(type: BlogPost['relatedType'], id: number) {
  return `related-${type.toLowerCase()}-${id}`
}

function highlightItem(type: BlogPost['relatedType'], id: number) {
  highlightedItemKey.value = `${type}:${id}`
  window.setTimeout(() => {
    highlightedItemKey.value = ''
  }, 2400)
}

function isHighlighted(type: BlogPost['relatedType'], id: number) {
  return highlightedItemKey.value === `${type}:${id}`
}

async function scrollToRelatedItem(type: BlogPost['relatedType'], id: number) {
  await nextTick()
  document.getElementById(itemDomId(type, id))?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  highlightItem(type, id)
}

function setTrimmedParam(params: URLSearchParams, key: string, value?: string) {
  const text = value?.trim()
  if (text) params.set(key, text)
}

// 城市是位置上下文而不是筛选条件，放到请求头可避免中文城市反复出现在 URL 中。
function locationCityHeader(city = placeQuery.city): HeadersInit {
  const value = city?.trim()
  // HTTP 自定义头应保持 ASCII，避免中文头在浏览器、Nginx 与后端之间发生编码歧义。
  return value ? { 'X-Location-City': encodeURIComponent(value) } : {}
}

function setPagingParams(params: URLSearchParams, page: number, pageSize: number, defaultPageSize: number) {
  if (page > 1) params.set('page', String(page))
  if (pageSize !== defaultPageSize) params.set('size', String(pageSize))
}

async function loadPlaces(page = 1, append = false) {
  const params = new URLSearchParams()
  if (selectedSport.value) params.set('sport', selectedSport.value)
  params.set('lng', String(placeQuery.lng))
  params.set('lat', String(placeQuery.lat))
  if (placeQuery.radius !== DEFAULT_PLACE_RADIUS) params.set('radius', String(placeQuery.radius))
  setPagingParams(params, page, PLACE_PAGE_SIZE, 20)
  setTrimmedParam(params, 'keyword', placeQuery.keyword)
  const result = await api<PageResult<Place>>(`/places/nearby?${params}`, {
    headers: locationCityHeader()
  })
  places.value = append ? appendById(places.value, result.records) : result.records
  placesPage.value = result.page
  placesTotal.value = result.total
  if (!append && selectedPlace.value && !result.records.some((place) => place.id === selectedPlace.value?.id)) {
    selectedPlace.value = null
    venueReviews.value = []
  }
}

async function loadVenueItemsForPlace(place: Place) {
  if (hasLoadedVenueItems(place)) {
    return venueItems.value[place.id] || []
  }
  try {
    const params = new URLSearchParams({
      placeRank: String(placeRankFor(place)),
      limit: '6'
    })
    const productSport = selectedSport.value || place.sportCode
    if (productSport) params.set('sport', productSport)
    const productsForPlace = await api<VenueItem[]>(`/items/1?${params}`)
    const mappedVenueItems = productsForPlace.map((product) => productWithPlaceContext(product, place))
    venueItems.value = {
      ...venueItems.value,
      [place.id]: mappedVenueItems
    }
    return mappedVenueItems
  } catch {
    venueItems.value = {
      ...venueItems.value,
      [place.id]: []
    }
    return []
  }
}

async function loadVenueSaleItems(page = 1, append = false) {
  const params = new URLSearchParams()
  if (selectedSport.value) params.set('sport', selectedSport.value)
  if (venueSaleView.value?.productType) params.set('category', venueSaleView.value.productType)
  setTrimmedParam(params, 'keyword', placeQuery.keyword)
  setPagingParams(params, page, 12, 12)
  const result = await api<PageResult<VenueItem>>(`/items/1?${params}`)
  const mappedRecords = result.records.map((product) => productWithPlaceContext(product, placeForEquipmentItem(product)))
  venueSaleItems.value = append ? appendById(venueSaleItems.value, mappedRecords) : mappedRecords
  venueSalesPage.value = result.page
  venueSalesTotal.value = result.total
}

async function loadEquipmentItems(page = 1, append = false) {
  if (productCategorySport.value !== selectedSport.value) {
    await loadEquipmentItemCategories()
  }
  const params = new URLSearchParams()
  if (selectedSport.value) params.set('sport', selectedSport.value)
  if (productQuery.categoryId) params.set('categoryId', productQuery.categoryId)
  setTrimmedParam(params, 'keyword', productQuery.keyword)
  setPagingParams(params, page, PRODUCT_PAGE_SIZE, 12)
  const result = await api<PageResult<EquipmentItem>>(`/items/2?${params}`)
  products.value = append ? appendById(products.value, result.records) : result.records
  productsPage.value = result.page
  productsTotal.value = result.total
}

async function loadEquipmentItemCategories() {
  if (!selectedSport.value) {
    categories.value = []
    productCategorySport.value = selectedSport.value
    return
  }
  categories.value = await api<Category[]>(`/categories/2?${new URLSearchParams({ sport: selectedSport.value })}`)
  productCategorySport.value = selectedSport.value
}

async function loadSeckill() {
  const params = new URLSearchParams()
  if (selectedSport.value) params.set('sport', selectedSport.value)
  if (productQuery.categoryId) params.set('categoryId', productQuery.categoryId)
  seckillActivities.value = await api<SeckillActivity[]>(`/seckill/2?${params}`)
}

function isDefaultRecommendBlogRequest(page: number, append: boolean) {
  return !append
    && page === 1
    && blogChannel.value === 'recommend'
    && !blogSport.value
    && !blogKeyword.value.trim()
}

async function fetchBlogs(page = 1, append = false) {
  // 博客接口会自行从 Token 解析当前用户。资料请求仅用于页面其它区域，和博客查询并行即可。
  const profileRequest = ensureUserProfile()
  if (blogChannel.value === 'follow' && !loggedIn.value) {
    blogs.value = []
    blogsPage.value = 1
    blogsTotal.value = 0
    followFeedLastId.value = undefined
    followFeedOffset.value = 0
    followFeedHasMore.value = false
    return
  }
  if (blogChannel.value === 'follow') {
    const params = new URLSearchParams()
    if (append && followFeedLastId.value) {
      params.set('lastId', String(followFeedLastId.value))
      params.set('offset', String(followFeedOffset.value))
    }
    const result = await api<ScrollResult<BlogPost>>(`/blogs/of/follow?${params}`)
    const records = result.list || []
    blogs.value = append ? appendById(blogs.value, records) : records
    blogsPage.value = page
    blogsTotal.value = blogs.value.length
    followFeedLastId.value = result.minTime || undefined
    followFeedOffset.value = result.offset || 0
    followFeedHasMore.value = Boolean(result.minTime) && records.length >= BLOG_PAGE_SIZE
    await profileRequest
    return
  }
  const params = new URLSearchParams({
    channel: 'recommend'
  })
  if (blogChannel.value === 'sport' && blogSport.value) params.set('sport', blogSport.value)
  setTrimmedParam(params, 'keyword', blogKeyword.value)
  setPagingParams(params, page, BLOG_PAGE_SIZE, 10)
  const result = await api<PageResult<BlogPost>>(`/blogs?${params}`)
  blogs.value = append ? appendById(blogs.value, result.records) : result.records
  blogsPage.value = result.page
  blogsTotal.value = result.total
  await profileRequest
}

async function loadBlogs(page = 1, append = false, force = false) {
  const isDefaultRecommend = isDefaultRecommendBlogRequest(page, append)
  const token = authToken.value || null
  if (isDefaultRecommend && !force && recommendedBlogsLoadedToken === token && blogs.value.length) {
    return
  }
  if (isDefaultRecommend && !force && recommendedBlogsRequest && recommendedBlogsRequestToken === token) {
    return recommendedBlogsRequest
  }

  const request = fetchBlogs(page, append)
  if (!isDefaultRecommend) {
    return request
  }
  recommendedBlogsRequest = request
  recommendedBlogsRequestToken = token
  try {
    await request
    recommendedBlogsLoadedToken = token
  } finally {
    if (recommendedBlogsRequest === request) {
      recommendedBlogsRequest = null
      recommendedBlogsRequestToken = undefined
    }
  }
}

function preloadRecommendedBlogs() {
  const preload = () => {
    void loadBlogs().catch(() => {
      // 预取失败不影响首页；真正进入社区时会按正常流程重试。
    })
  }
  const idleWindow = window as Window & {
    requestIdleCallback?: (callback: IdleRequestCallback, options?: IdleRequestOptions) => number
  }
  if (idleWindow.requestIdleCallback) {
    idleWindow.requestIdleCallback(preload, { timeout: 2500 })
  } else {
    window.setTimeout(preload, 700)
  }
}

async function loadSeckillCategories() {
  if (!selectedSport.value) {
    seckillCategories.value = []
    seckillCategorySport.value = selectedSport.value
    return
  }
  const categoryParams = new URLSearchParams({ sport: selectedSport.value })
  seckillCategories.value = await api<Category[]>(`/categories/2?${categoryParams}`)
  seckillCategorySport.value = selectedSport.value
}

async function loadPlayers(page = 1, append = false) {
  const params = new URLSearchParams()
  if (selectedSport.value) params.set('sport', selectedSport.value)
  if (!loggedIn.value) {
    params.set('lng', String(placeQuery.lng))
    params.set('lat', String(placeQuery.lat))
  }
  setPagingParams(params, page, SOCIAL_PAGE_SIZE, 12)
  const playersResult = await api<PageResult<Player>>(`/social/players?${params}`, {
    headers: locationCityHeader()
  })
  players.value = append ? appendByKey(players.value, playersResult.records, (player) => player.userId) : playersResult.records
  playersPage.value = playersResult.page
  playersTotal.value = playersResult.total
}

async function loadActivities(page = 1, append = false) {
  const params = new URLSearchParams({ scope: activityScope.value })
  if (selectedSport.value) params.set('sport', selectedSport.value)
  setPagingParams(params, page, SOCIAL_PAGE_SIZE, 12)
  const activitiesResult = await api<PageResult<SportActivity>>(`/social/activities?${params}`, {
    headers: locationCityHeader()
  })
  activities.value = append ? appendById(activities.value, activitiesResult.records) : activitiesResult.records
  activitiesPage.value = activitiesResult.page
  activitiesTotal.value = activitiesResult.total
}

async function loadSocial(page = 1, append = false) {
  await Promise.all([
    loadPlayers(page, append),
    loadActivities(page, append)
  ])
}

async function loadCartItems(force = false) {
  if (!loggedIn.value) {
    cartItems.value = []
    cartCount.value = 0
    cartLoaded.value = false
    return
  }
  if (cartLoaded.value && !force) return
  cartItems.value = await api<CartItem[]>('/cart')
  cartCount.value = cartItems.value.reduce((sum, item) => sum + item.quantity, 0)
  cartLoaded.value = true
}

async function loadVenueOrders() {
  if (!loggedIn.value) {
    venueOrders.value = []
    return
  }
  venueOrders.value = await api<ProfileOrderCard[]>('/orders/1')
}

async function loadEquipmentOrders() {
  if (!loggedIn.value) {
    equipmentOrders.value = []
    return
  }
  equipmentOrders.value = await api<ProfileOrderCard[]>('/orders/2')
}

async function loadOrderBundle(force = false) {
  if (!loggedIn.value) {
    venueOrders.value = []
    equipmentOrders.value = []
    ordersLoaded.value = false
    return
  }
  if (ordersLoaded.value && !force) return
  await Promise.all([loadVenueOrders(), loadEquipmentOrders()])
  ordersLoaded.value = true
}

async function loadProfileViewData(view: ProfileView, force = false) {
  if (view === 'cart') {
    await loadCartItems(force)
    return
  }
  await loadOrderBundle(force)
}

async function loadUserProfile() {
  if (!loggedIn.value) {
    userProfile.value = null
    return
  }
  userProfile.value = await api<UserProfile>('/auth/me')
}

async function loadSocialProfile() {
  if (!loggedIn.value) {
    socialProfile.value = null
    return
  }
  try {
    socialProfile.value = await api<Player>('/social/profile/me')
  } catch {
    socialProfile.value = null
  }
}

async function ensureUserProfile() {
  if (!loggedIn.value || userProfile.value) return
  try {
    await loadUserProfile()
  } catch {
    userProfile.value = null
  }
}

async function loadPublicProfile(userId: number) {
  viewedUserProfile.value = await api<UserPublicProfile>(`/auth/users/${userId}`)
}

async function loadProfileBlogs(userId: number, page = 1, append = false) {
  const params = new URLSearchParams()
  setPagingParams(params, page, BLOG_PAGE_SIZE, 10)
  const result = await api<PageResult<BlogPost>>(`/blogs/of/user/${userId}?${params}`)
  profileBlogs.value = append ? appendById(profileBlogs.value, result.records) : result.records
  profileBlogsPage.value = result.page
  profileBlogsTotal.value = result.total
}

async function loadMyProfileHome(force = false) {
  if (!loggedIn.value) {
    userProfile.value = null
    profileBlogs.value = []
    return
  }
  if (force || !userProfile.value) {
    await loadUserProfile()
  }
  if (force || !socialProfile.value) {
    await loadSocialProfile()
  }
  if (userProfile.value) {
    await loadProfileBlogs(userProfile.value.id)
  }
}

async function loadCurrentTab() {
  const tasks: Promise<unknown>[] = []
  if (activeTab.value === 'home') {
    tasks.push(venueSaleView.value ? loadVenueSaleItems() : loadPlaces())
  }
  if (activeTab.value === 'seckill') {
    tasks.push(loadBlogs())
  }
  if (activeTab.value === 'social') {
    tasks.push(loadSocial())
    tasks.push(ensureActivityPlaceOptions())
  }
  if (activeTab.value === 'equipment') {
    tasks.push(loadEquipmentItems())
    tasks.push(loadSeckill())
  }
  if (activeTab.value === 'profile') {
    tasks.push(profileMode.value === 'public' && viewedUserProfile.value
      ? loadProfileBlogs(viewedUserProfile.value.id)
      : loadMyProfileHome())
  }
  if (activeTab.value === 'assistant') {
    tasks.push(loadAgentStatus())
    tasks.push(loadLatestAgentHistory())
  }
  const failed = (await Promise.allSettled(tasks)).find((result) => result.status === 'rejected')
  if (failed && failed.status === 'rejected') {
    message.value = handleRequestError(failed.reason)
  }
}

function currentScrollTop() {
  return Math.max(0, document.scrollingElement?.scrollTop || window.scrollY || document.documentElement.scrollTop || 0)
}

function nearPageBottom() {
  const scrollTop = currentScrollTop()
  const viewport = window.innerHeight || document.documentElement.clientHeight
  const height = document.scrollingElement?.scrollHeight || document.documentElement.scrollHeight
  return scrollTop + viewport >= height - 180
}

async function loadMoreCurrentTab() {
  if (loading.value || loadingMore.value) return
  if (activeTab.value === 'home' && selectedPlace.value) return
  if (activeTab.value === 'home' && venueSaleView.value && !hasMoreVenueSales.value) return
  if (activeTab.value === 'home' && !venueSaleView.value && !hasMorePlaces.value) return
  if (activeTab.value === 'equipment' && !hasMoreEquipmentItems.value) return
  if (activeTab.value === 'social' && !hasMoreSocial.value) return
  if (activeTab.value === 'seckill' && !hasMoreBlogs.value) return
  if (activeTab.value === 'profile' && (!hasMoreProfileBlogs.value || profileOrderPageVisible.value)) return
  if (!['home', 'equipment', 'social', 'seckill', 'profile'].includes(activeTab.value)) return

  loadingMore.value = true
  try {
    if (activeTab.value === 'home') {
      if (venueSaleView.value) {
        await loadVenueSaleItems(venueSalesPage.value + 1, true)
      } else {
        await loadPlaces(placesPage.value + 1, true)
      }
    } else if (activeTab.value === 'equipment') {
      await loadEquipmentItems(productsPage.value + 1, true)
    } else if (activeTab.value === 'social') {
      const nextPage = Math.max(playersPage.value, activitiesPage.value) + 1
      await loadSocial(nextPage, true)
    } else if (activeTab.value === 'seckill') {
      await loadBlogs(blogsPage.value + 1, true)
    } else if (activeTab.value === 'profile') {
      const userId = profileMode.value === 'public' ? viewedUserProfile.value?.id : userProfile.value?.id
      if (userId) {
        await loadProfileBlogs(userId, profileBlogsPage.value + 1, true)
      }
    }
  } catch (error) {
    message.value = handleRequestError(error)
  } finally {
    loadingMore.value = false
  }
}

function handleWindowScroll() {
  updateHeaderVisibility()
  if (nearPageBottom()) {
    void loadMoreCurrentTab()
  }
}

function updateHeaderVisibility() {
  if (!showPhoneHeader.value) return
  const current = currentScrollTop()
  const delta = current - lastHeaderScrollTop
  if (current <= 12) {
    headerVisible.value = true
    headerScrollUpDistance = 0
    headerScrollDownDistance = 0
    lastHeaderScrollTop = current
    return
  }
  if (Math.abs(delta) < 1) return
  if (delta < 0) {
    headerScrollUpDistance += Math.abs(delta)
    headerScrollDownDistance = 0
    if (headerScrollUpDistance >= HEADER_SHOW_ACCUMULATE) {
      headerVisible.value = true
      headerScrollUpDistance = 0
    }
  } else if (current > HEADER_HIDE_AFTER) {
    headerScrollDownDistance += delta
    headerScrollUpDistance = 0
    if (headerScrollDownDistance >= Math.max(HEADER_SCROLL_DELTA, HEADER_HIDE_ACCUMULATE)) {
      headerVisible.value = false
      headerScrollDownDistance = 0
    }
  }
  lastHeaderScrollTop = current
}

function resetHeaderVisibility() {
  headerVisible.value = true
  headerScrollUpDistance = 0
  headerScrollDownDistance = 0
  lastHeaderScrollTop = currentScrollTop()
}

function scrollToPageTop() {
  window.scrollTo({ top: 0, behavior: 'auto' })
  resetHeaderVisibility()
}

function refreshActivityWindowIfExpired(force = false) {
  const currentStart = activityForm.startTime ? new Date(activityForm.startTime) : null
  if (!force && currentStart && currentStart > new Date()) return
  const window = nextActivityWindow()
  activityForm.startTime = window.startTime
  activityForm.endTime = window.endTime
}

async function switchTab(tab: Tab) {
  activeTab.value = tab
  if (tab !== 'home') {
    homeReturnTab.value = null
  }
  selectedBlog.value = null
  relatedBlogReturn.value = null
  blogComposerVisible.value = false
  profileEditorVisible.value = false
  if (tab === 'social') {
    refreshActivityWindowIfExpired()
  }
  if (tab === 'profile') {
    profileMode.value = 'me'
    viewedUserProfile.value = null
    profileView.value = 'orders'
    profileOrderPageVisible.value = false
    profileBlogs.value = []
    profileBlogsPage.value = 1
    profileBlogsTotal.value = 0
  }
  await wrap(loadCurrentTab)
  if (tab === 'assistant') {
    await positionAgentOnEntry()
  }
}

function logout() {
  resetLoginState()
  activeTab.value = 'home'
  message.value = '已退出登录'
}

function openAuthPage(returnTab: Tab = activeTab.value) {
  authReturnTab.value = returnTab
  authPageVisible.value = true
}

function closeAuthPage() {
  authPageVisible.value = false
}

function requireLogin(tip = '请先登录后使用') {
  if (loggedIn.value) return true
  openAuthPage(activeTab.value)
  message.value = tip
  return false
}

function sportNameByCode(code?: string) {
  return sports.value.find((sport) => sport.code === code)?.name || '运动'
}

function defaultActivitySportCode(place?: Place) {
  if (place?.sportCode && place.sportCode !== 'all') return place.sportCode
  if (selectedSport.value) return selectedSport.value
  return sports.value[0]?.code || 'badminton'
}

function updateActivityTitle(sportCode = activityForm.sportCode) {
  const venueName = activityForm.venueName && activityForm.venueName !== '先从场所列表选择' ? activityForm.venueName : ''
  if (venueName) {
    activityForm.title = `${sportNameByCode(sportCode)}约球 @ ${venueName}`
  }
}

function changeActivitySport() {
  updateActivityTitle()
}

async function changeActivityScope(scope: ActivityScope) {
  if ((scope === 'created' || scope === 'joined') && !requireLogin('请先登录后查看我的约球活动')) return
  if (activityScope.value === scope) return
  activityScope.value = scope
  activities.value = []
  activitiesPage.value = 1
  activitiesTotal.value = 0
  await wrap(() => loadActivities())
}

function applyActivityPlace(place: Place) {
  activityForm.placeSource = place.source || 'amap'
  activityForm.placeId = place.id
  activityForm.venueName = place.name
  activityForm.city = place.city || locationLabel.value
  activityForm.sportCode = defaultActivitySportCode(place)
  updateActivityTitle(activityForm.sportCode)
}

async function ensureActivityPlaceOptions() {
  if (!places.value.length) {
    await loadPlaces()
  }
}

function changeActivityPlace() {
  const place = places.value.find((item) => item.id === activityForm.placeId)
  if (place) {
    applyActivityPlace(place)
  }
}

async function refreshProfile() {
  await wrap(async () => {
    if (profileMode.value === 'public' && viewedUserProfile.value) {
      await Promise.all([
        loadPublicProfile(viewedUserProfile.value.id),
        loadProfileBlogs(viewedUserProfile.value.id)
      ])
      return
    }
    await loadMyProfileHome(true)
    if (profileOrderPageVisible.value) {
      await loadProfileViewData(profileView.value, true)
    }
  }, '我的页面已刷新')
}

async function changeSport(code: string) {
  selectedSport.value = code
  productQuery.categoryId = ''
  seckillQuery.categoryId = ''
  activityForm.sportCode = code || 'badminton'
  selectedPlace.value = null
  venueReviews.value = []
  venueSaleItems.value = []
  venueItems.value = {}
  inventoriesByVenueItem.value = {}
  products.value = []
  categories.value = []
  seckillCategories.value = []
  productCategorySport.value = ''
  seckillCategorySport.value = ''
  blogs.value = []
  players.value = []
  activities.value = []
  resetListPaging()
  await wrap(loadCurrentTab)
}

async function selectEquipmentItemCategory(categoryId: string) {
  productQuery.categoryId = categoryId
  products.value = []
  productsPage.value = 1
  productsTotal.value = 0
  await wrap(async () => {
    await Promise.all([loadEquipmentItems(), loadSeckill()])
  })
}

async function selectSeckillCategory(categoryId: string) {
  seckillQuery.categoryId = categoryId
  await wrap(loadSeckill)
}

async function selectBlogChannel(channel: BlogChannel, sport = '') {
  if (channel === 'follow' && !requireLogin('请先登录后查看关注动态')) return
  blogChannel.value = channel
  blogSport.value = sport
  blogs.value = []
  blogsPage.value = 1
  blogsTotal.value = 0
  followFeedLastId.value = undefined
  followFeedOffset.value = 0
  followFeedHasMore.value = true
  await wrap(loadBlogs)
}

function syncBlogLikeState(blogId: number, isLiked: boolean, liked: number) {
  const apply = (item: BlogPost) => item.id === blogId ? { ...item, isLiked, liked } : item
  blogs.value = blogs.value.map(apply)
  profileBlogs.value = profileBlogs.value.map(apply)
  if (selectedBlog.value?.id === blogId) {
    selectedBlog.value = { ...selectedBlog.value, isLiked, liked }
  }
}

function syncBlogFollowState(userId: number, followed: boolean) {
  const apply = (item: BlogPost) => item.userId === userId ? { ...item, followed } : item
  blogs.value = blogs.value.map(apply)
  profileBlogs.value = profileBlogs.value.map(apply)
  if (selectedBlog.value?.userId === userId) {
    selectedBlog.value = { ...selectedBlog.value, followed }
  }
  if (viewedUserProfile.value?.id === userId) {
    viewedUserProfile.value = { ...viewedUserProfile.value, followed }
  }
}

function isOwnBlog(blog?: BlogPost | null) {
  return Boolean(loggedIn.value && blog && userProfile.value?.id === blog.userId)
}

function removeBlogFromState(blogId: number) {
  blogs.value = blogs.value.filter((item) => item.id !== blogId)
  profileBlogs.value = profileBlogs.value.filter((item) => item.id !== blogId)
  blogsTotal.value = Math.max(0, blogsTotal.value - 1)
  profileBlogsTotal.value = Math.max(0, profileBlogsTotal.value - 1)
  if (selectedBlog.value?.id === blogId) {
    selectedBlog.value = null
  }
}

async function toggleBlogLike(blog: BlogPost) {
  if (!requireLogin('请先登录后点赞动态')) return
  const liked = blog.isLiked
  blog.isLiked = !liked
  blog.liked += liked ? -1 : 1
  syncBlogLikeState(blog.id, blog.isLiked, blog.liked)
  try {
    await api(`/blogs/${blog.id}/like`, { method: 'PUT' })
  } catch (error) {
    blog.isLiked = liked
    blog.liked += liked ? 1 : -1
    syncBlogLikeState(blog.id, blog.isLiked, blog.liked)
    message.value = handleRequestError(error)
  }
}

async function deleteBlog(blog: BlogPost) {
  if (!isOwnBlog(blog)) return
  if (!window.confirm('确定删除这条动态吗？')) return
  await wrap(async () => {
    await api(`/blogs/${blog.id}`, { method: 'DELETE' })
    removeBlogFromState(blog.id)
  }, '动态已删除')
}

async function toggleBlogFollow(blog: BlogPost) {
  if (!requireLogin('请先登录后关注作者')) return
  const next = !blog.followed
  await wrap(async () => {
    await api(`/follows/${blog.userId}/${next}`, { method: 'PUT' })
    syncBlogFollowState(blog.userId, next)
  }, next ? '已关注作者' : '已取消关注')
}

async function openBlogDetail(blog: BlogPost) {
  await wrap(async () => {
    await ensureUserProfile()
    relatedBlogReturn.value = null
    selectedBlog.value = await api<BlogPost>(`/blogs/${blog.id}`)
  })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function closeBlogDetail() {
  selectedBlog.value = null
  relatedBlogReturn.value = null
}

function rememberBlogReturn(blog: BlogPost) {
  relatedBlogReturn.value = {
    ...blog,
    images: [...blog.images]
  }
}

async function returnToBlogDetail() {
  const blog = relatedBlogReturn.value
  relatedBlogReturn.value = null
  selectedPlace.value = null
  venueReviews.value = []
  venueSaleView.value = null
  venueSaleItems.value = []
  activeTab.value = 'seckill'
  if (!blog) {
    await wrap(loadBlogs)
    return
  }
  selectedBlog.value = blog
  await nextTick()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function selectedBlogRelatedOption() {
  return blogRelatedOptions.value.find((item) => item.key === blogPublishForm.relatedKey)
}

function fillBlogPublishDraft(option?: BlogRelatedOption) {
  const related = option || selectedBlogRelatedOption() || blogRelatedOptions.value[0]
  if (!related) return
  blogPublishForm.relatedKey = related.key
  blogPublishForm.sportCode = related.sportCode || blogPublishForm.sportCode
  if (!blogPublishForm.title.trim()) {
    blogPublishForm.title = `${sportNameByCode(related.sportCode)}体验：${related.title}`
  }
  if (!blogPublishForm.content.trim()) {
    blogPublishForm.content = `今天体验了「${related.title}」，整体感受不错，适合周末约球或者下班后放松。`
  }
  if (!blogPublishForm.imageUrls.length && related.coverUrl) {
    blogPublishForm.imageUrls = [related.coverUrl]
  }
}

async function openBlogPublisher() {
  if (!requireLogin('请先登录后发布动态')) return
  blogComposerReturnTab.value = activeTab.value
  activeTab.value = 'seckill'
  selectedBlog.value = null
  blogComposerVisible.value = true
  const contextSport = blogChannel.value === 'sport' && blogSport.value ? blogSport.value : selectedSport.value
  blogPublishForm.sportCode = contextSport || blogPublishForm.sportCode || sports.value[0]?.code || 'badminton'
  await wrap(async () => {
    if (!products.value.length) {
      await loadEquipmentItems()
    }
    if (!venueSaleItems.value.length) {
      await loadVenueSaleItems()
    }
    fillBlogPublishDraft()
  })
  await nextTick()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function closeBlogPublisher() {
  blogComposerVisible.value = false
  activeTab.value = blogComposerReturnTab.value
}

function changeBlogPublishSport() {
  const first = blogRelatedOptions.value[0]
  blogPublishForm.relatedKey = first?.key || ''
  if (first && (!blogPublishForm.title.trim() || !blogPublishForm.content.trim())) {
    fillBlogPublishDraft(first)
  }
}

function changeBlogRelatedOption() {
  fillBlogPublishDraft()
}

async function publishBlog() {
  if (!requireLogin('请先登录后发布动态')) return
  const related = selectedBlogRelatedOption()
  if (!related) {
    message.value = '请先选择要关联的装备或场馆团购'
    return
  }
  if (!blogPublishForm.title.trim()) {
    message.value = '请填写动态标题'
    return
  }
  if (!blogPublishForm.content.trim()) {
    message.value = '请填写动态正文'
    return
  }
  const images = blogPublishForm.imageUrls
  await wrap(async () => {
    await api<{ blogId: number }>('/blogs', {
      method: 'POST',
      body: JSON.stringify({
        sportCode: blogPublishForm.sportCode,
        title: blogPublishForm.title,
        content: blogPublishForm.content,
        images,
        relatedType: related.type,
        relatedId: related.id,
        relatedTitle: related.title,
        relatedCoverUrl: related.coverUrl,
        relatedPrice: related.price
      })
    })
    blogComposerVisible.value = false
    blogPublishForm.title = ''
    blogPublishForm.content = ''
    blogPublishForm.imageUrls = []
    blogPublishForm.relatedKey = ''
    blogChannel.value = 'recommend'
    blogSport.value = ''
    blogsPage.value = 1
    blogsTotal.value = 0
    await loadBlogs()
    if (userProfile.value) {
      await loadProfileBlogs(userProfile.value.id)
    }
  }, '动态已发布')
}

async function openUserProfile(userId: number) {
  if (loggedIn.value && !userProfile.value) {
    try {
      await loadUserProfile()
    } catch {
      userProfile.value = null
    }
  }
  if (loggedIn.value && userProfile.value?.id === userId) {
    await switchTab('profile')
    return
  }
  if (!(activeTab.value === 'profile' && profileMode.value === 'public')) {
    publicProfileReturnTab.value = activeTab.value
  }
  selectedBlog.value = null
  activeTab.value = 'profile'
  profileMode.value = 'public'
  profileOrderPageVisible.value = false
  profileView.value = 'orders'
  viewedUserProfile.value = null
  profileBlogs.value = []
  profileBlogsPage.value = 1
  profileBlogsTotal.value = 0
  await wrap(async () => {
    await Promise.all([
      loadPublicProfile(userId),
      loadProfileBlogs(userId)
    ])
  })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function backFromPublicProfile() {
  const target = publicProfileReturnTab.value
  profileMode.value = 'me'
  viewedUserProfile.value = null
  profileBlogs.value = []
  profileBlogsPage.value = 1
  profileBlogsTotal.value = 0
  await switchTab(target)
}

async function toggleProfileFollow() {
  if (!viewedUserProfile.value || viewedUserProfile.value.isMe) return
  if (!requireLogin('请先登录后关注球友')) return
  const userId = viewedUserProfile.value.id
  const next = !viewedUserProfile.value.followed
  await wrap(async () => {
    await api(`/follows/${userId}/${next}`, { method: 'PUT' })
    if (viewedUserProfile.value) {
      syncBlogFollowState(userId, next)
      viewedUserProfile.value = { ...viewedUserProfile.value, followed: next }
    }
  }, next ? '已关注球友' : '已取消关注')
}

async function openBlogRelated(blog: BlogPost) {
  rememberBlogReturn(selectedBlog.value || blog)
  selectedBlog.value = null
  if (blog.relatedType === 'EQUIPMENT') {
    activeTab.value = 'equipment'
    productQuery.keyword = ''
    productQuery.categoryId = ''
    productsPage.value = 1
    productsTotal.value = 0
    await wrap(async () => {
      const [detail] = await Promise.all([
        api<EquipmentItem>(`/items/2/${blog.relatedId}`),
        loadEquipmentItems(),
        loadSeckill()
      ])
      products.value = [detail, ...products.value.filter((item) => item.id !== detail.id)]
      await scrollToRelatedItem('EQUIPMENT', detail.id)
    })
    return
  }
  activeTab.value = 'home'
  selectedPlace.value = null
  venueReviews.value = []
  venueSaleView.value = { productType: '', title: '场馆团购', subtitle: '来自社区动态的关联项目' }
  venueSalesPage.value = 1
  venueSalesTotal.value = 0
  await wrap(async () => {
    if (!places.value.length) {
      await loadPlaces()
    }
    const [detail] = await Promise.all([
      api<VenueItem>(`/items/1/${blog.relatedId}`),
      loadVenueSaleItems()
    ])
    const mapped = productWithPlaceContext(detail, placeForEquipmentItem(detail))
    venueSaleItems.value = [mapped, ...venueSaleItems.value.filter((item) => item.id !== mapped.id)]
    await scrollToRelatedItem('VENUE_PRODUCT', mapped.id)
  })
}

async function openProfileView(view: ProfileView) {
  profileView.value = view
  profileOrderPageVisible.value = true
  profileEditorVisible.value = false
  await wrap(() => loadProfileViewData(view))
}

function closeProfileOrderPage() {
  profileOrderPageVisible.value = false
}

function fillProfileForm(profile?: Player | null) {
  profileForm.phone = userProfile.value?.phone || ''
  profileForm.username = userProfile.value?.username || ''
  profileForm.email = userProfile.value?.email || ''
  profileForm.password = ''
  profileForm.nickname = userProfile.value?.nickname || ''
  profileForm.avatar = userProfile.value?.avatar || ''
  profileForm.sportCode = profile?.sportCode || selectedSport.value || 'badminton'
  profileForm.city = profile?.city || userProfile.value?.city || placeQuery.city || '西安'
  profileForm.area = profile?.area || locationLabel.value.replace(profileForm.city, '').trim() || '未央区'
  profileForm.longitude = profile?.longitude ?? placeQuery.lng
  profileForm.latitude = profile?.latitude ?? placeQuery.lat
  profileForm.level = profile?.level || userProfile.value?.level || '中级'
  profileForm.playStyle = profile?.playStyle || '双打'
  profileForm.availableTime = profile?.availableTime || userProfile.value?.prefer_time || '周末下午'
  profileForm.intro = profile?.intro || '想找固定球友，工作日晚上和周末都可以约。'
  profileForm.allowInvite = profile?.allowInvite ?? true
}

async function openProfileEditor() {
  if (!requireLogin('请先登录后编辑球友资料')) return
  await wrap(async () => {
    if (!socialProfile.value) {
      await loadSocialProfile()
    }
    fillProfileForm(socialProfile.value)
    profileEditorVisible.value = true
  })
}

async function uploadFile(file: File, bizType: string, bizId?: number) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', bizType)
  if (bizId) {
    formData.append('bizId', String(bizId))
  }
  return api<FileMetadata>('/files/upload', {
    method: 'POST',
    body: formData
  })
}

async function uploadBlogImages(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return
  await wrap(async () => {
    const uploaded = await Promise.all(files.map((file) => uploadFile(file, 'blog')))
    blogPublishForm.imageUrls = [
      ...blogPublishForm.imageUrls,
      ...uploaded.map((file) => file.publicUrl)
    ].slice(0, 9)
  }, '图片已上传')
  input.value = ''
}

function removeBlogImage(url: string) {
  blogPublishForm.imageUrls = blogPublishForm.imageUrls.filter((item) => item !== url)
}

function closeProfileEditor() {
  profileEditorVisible.value = false
}

async function saveSocialProfile() {
  if (!profileForm.city.trim() || !profileForm.area.trim() || !profileForm.level.trim()) {
    message.value = '请补全城市、区域和水平'
    return
  }
  await wrap(async () => {
    await api('/auth/me', {
      method: 'PUT',
      body: JSON.stringify({
        username: profileForm.username.trim(),
        phone: profileForm.phone.trim(),
        email: profileForm.email.trim(),
        password: profileForm.password.trim(),
        nickname: profileForm.nickname.trim(),
        avatar: profileForm.avatar.trim(),
        city: profileForm.city.trim(),
        level: profileForm.level.trim(),
        preferTime: profileForm.availableTime.trim()
      })
    })
    await api('/social/profile/me', {
      method: 'POST',
      body: JSON.stringify({
        sportCode: profileForm.sportCode,
        city: profileForm.city.trim(),
        area: profileForm.area.trim(),
        longitude: profileForm.longitude,
        latitude: profileForm.latitude,
        level: profileForm.level.trim(),
        playStyle: profileForm.playStyle.trim(),
        availableTime: profileForm.availableTime.trim(),
        intro: profileForm.intro.trim(),
        allowInvite: profileForm.allowInvite
      })
    })
    await Promise.all([loadUserProfile(), loadSocialProfile()])
    profileEditorVisible.value = false
  }, '球友资料已更新')
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  await wrap(async () => {
    const uploaded = await uploadFile(file, 'avatar')
    profileForm.avatar = uploaded.publicUrl
  }, '头像已上传')
  input.value = ''
}

function searchPlaces() {
  wrap(async () => {
    if (activeTab.value === 'home') {
      if (venueSaleView.value) {
        venueSalesPage.value = 1
        venueSalesTotal.value = 0
        await loadVenueSaleItems()
        return
      }
      selectedPlace.value = null
      venueReviews.value = []
      venueItems.value = {}
      inventoriesByVenueItem.value = {}
      placeQuery.radius = DEFAULT_PLACE_RADIUS
      placesPage.value = 1
      placesTotal.value = 0
      await loadPlaces()
      return
    }
    if (activeTab.value === 'social') {
      playersPage.value = 1
      playersTotal.value = 0
      activitiesPage.value = 1
      activitiesTotal.value = 0
      await loadSocial()
      return
    }
    if (activeTab.value === 'equipment') {
      productsPage.value = 1
      productsTotal.value = 0
      await Promise.all([loadEquipmentItems(), loadSeckill()])
      return
    }
    if (activeTab.value === 'seckill') {
      blogsPage.value = 1
      blogsTotal.value = 0
      await loadBlogs()
      return
    }
    await loadCurrentTab()
  })
}

async function refreshCurrentLocation(options: { reload?: boolean; showMessage?: boolean } = {}) {
  const reload = options.reload ?? true
  const showMessage = options.showMessage ?? true
  locating.value = true
  try {
    let located: PreciseLocation | IpLocation
    try {
      located = await locateWithAmapFirst()
    } catch (preciseLocationError) {
      const fallback = await api<IpLocation>('/places/ip-location')
      if (!Number.isFinite(fallback.lng) || !Number.isFinite(fallback.lat)) {
        throw preciseLocationError
      }
      located = fallback
    }
    const lng = Number(located.lng)
    const lat = Number(located.lat)
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
      throw new Error('定位结果缺少有效坐标')
    }
    const accuracy = Math.round(Number(located.accuracy || 0))
    locationAccuracy.value = accuracy
    placeQuery.lng = lng
    placeQuery.lat = lat
    placeQuery.radius = DEFAULT_PLACE_RADIUS
    if (located.source === 'ip' || located.source === 'default') {
      placeQuery.city = located.city || placeQuery.city
      placeQuery.preciseAddress = located.shortAddress || located.formattedAddress || ''
    } else {
      try {
        const location = await api<RegeoLocation>(`/places/regeo?${new URLSearchParams({
          lng: String(placeQuery.lng),
          lat: String(placeQuery.lat)
        })}`)
        placeQuery.city = location.city || ''
        placeQuery.preciseAddress = location.shortAddress || location.formattedAddress || ''
      } catch {
        placeQuery.city = ''
        placeQuery.preciseAddress = ''
      }
    }
    if (loggedIn.value && located.source !== 'ip' && located.source !== 'default') {
      await api('/auth/location', {
        method: 'PUT',
        body: JSON.stringify({
          city: placeQuery.city,
          preciseAddress: placeQuery.preciseAddress,
          lng: placeQuery.lng,
          lat: placeQuery.lat
        })
      })
    }
    if (reload) {
      await loadCurrentTab()
    }
    if (showMessage) {
      const sourceLabel = located.source === 'amap'
        ? '高德高精度定位'
        : located.source === 'browser'
          ? '浏览器定位'
          : located.source === 'ip'
            ? 'IP 大致定位'
            : '默认城市西安'
      message.value = located.source === 'ip'
        ? `浏览器精确定位不可用，已使用 IP 大致定位：${placeQuery.city}，精度约 ${accuracy / 1000} 公里`
        : located.source === 'default'
          ? '无法获取当前位置，已使用默认城市西安'
          : accuracy > 1000
        ? `已使用当前位置（${sourceLabel}），定位精度约 ${accuracy} 米；当前可能仍是电脑/Wi-Fi 网络定位`
        : `已使用当前位置（${sourceLabel}），定位精度约 ${accuracy} 米`
    }
    return true
  } catch (error) {
    if (showMessage) {
      throw new Error(geolocationMessage(error))
    }
    return false
  } finally {
    locating.value = false
  }
}

async function useCurrentLocation() {
  await wrap(async () => {
    await refreshCurrentLocation()
  })
}
function geolocationMessage(error: unknown) {
  if (error instanceof Error) return error.message
  return '获取当前位置失败'
}
async function showNearbyPlaces() {
  activeTab.value = 'home'
  venueSaleView.value = null
  selectedPlace.value = null
  venueReviews.value = []
  if (!places.value.length) {
    await wrap(loadPlaces)
  }
  await nextTick()
  document.getElementById('nearby-places')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function openVenueSalePage(productType: VenueSaleType, title: string, subtitle: string) {
  scrollToPageTop()
  activeTab.value = 'home'
  selectedPlace.value = null
  venueReviews.value = []
  venueSaleView.value = { productType, title, subtitle }
  venueSaleItems.value = []
  venueSalesPage.value = 1
  venueSalesTotal.value = 0
  await wrap(async () => {
    if (!places.value.length) {
      await loadPlaces()
    }
    await loadVenueSaleItems()
  })
  await nextTick()
  scrollToPageTop()
}

async function closeVenueSalePage() {
  if (relatedBlogReturn.value) {
    await returnToBlogDetail()
    return
  }
  const returnTab = homeReturnTab.value
  venueSaleView.value = null
  venueSaleItems.value = []
  venueSalesPage.value = 1
  venueSalesTotal.value = 0
  if (returnTab) {
    homeReturnTab.value = null
    activeTab.value = returnTab
    await nextTick()
    document.getElementById('agent-chat-bottom')?.scrollIntoView({ behavior: 'smooth', block: 'end' })
    return
  }
  if (!places.value.length) {
    await wrap(loadPlaces)
  }
}

async function openVenueSaleItemPlace(item: VenueItem) {
  if (!places.value.length) {
    await wrap(loadPlaces)
  }
  const place = (item.amapPlaceId
    ? places.value.find((record) => record.id === item.amapPlaceId)
    : null) || placeForEquipmentItem(item)
  if (!place) {
    message.value = '暂未找到该项目对应的场所'
    return
  }
  await selectPlace(place)
}

async function openAssistant(prefill = '') {
  activeTab.value = 'assistant'
  selectedBlog.value = null
  blogComposerVisible.value = false
  agentInput.value = ''
  agentInputPlaceholder.value = prefill || '问问附近场地、约球活动、装备推荐'
  await loadAgentStatus()
  await loadLatestAgentHistory()
  await positionAgentOnEntry()
}

function scrollAgentChatToBottom(behavior: ScrollBehavior = 'smooth') {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      document.getElementById('agent-chat-bottom')?.scrollIntoView({ behavior, block: 'end' })
    })
  })
}

function scrollAgentMessageIntoView(messageId: string, behavior: ScrollBehavior = 'smooth', block: ScrollLogicalPosition = 'end') {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      document.getElementById(`agent-message-${messageId}`)?.scrollIntoView({ behavior, block })
    })
  })
}

function latestAgentUserMessageId() {
  for (let index = agentMessages.value.length - 1; index >= 0; index--) {
    if (agentMessages.value[index].role === 'user') {
      return agentMessages.value[index].id
    }
  }
  return null
}

async function positionAgentOnEntry() {
  await nextTick()
  const messageId = latestAgentUserMessageId()
  if (!messageId) {
    scrollToPageTop()
    return
  }
  scrollAgentMessageIntoView(messageId, 'auto', 'start')
  requestAnimationFrame(() => requestAnimationFrame(resetHeaderVisibility))
}

function sportCodesFromAgentContext(reply = '') {
  const matched = sports.value
    .filter((sport) => reply.includes(sport.name))
    .map((sport) => sport.code)
  if (matched.length) return [...new Set(matched)]

  for (let index = agentMessages.value.length - 1; index >= 0; index -= 1) {
    const item = agentMessages.value[index]
    const cardSports = (item.cards || [])
      .map((card) => String(card.meta?.sportCode || card.action?.payload?.sportCode || ''))
      .filter(Boolean)
    if (cardSports.length) return [...new Set(cardSports)]

    const messageSports = sports.value
      .filter((sport) => item.content.includes(sport.name))
      .map((sport) => sport.code)
    if (messageSports.length) return [...new Set(messageSports)]
  }
  return []
}

function replyNeedsSportSelection(reply: string) {
  if (sportCodesFromAgentContext(reply).length) return false
  return !hasAgentConversation.value
}

function handleAgentQuickReply(reply: string) {
  if (replyNeedsSportSelection(reply)) {
    openAgentSportPicker(reply)
    return
  }

  const sportCodes = sportCodesFromAgentContext(reply)
  const alreadyNamesSport = sports.value.some((sport) => reply.includes(sport.name))
  const sportNames = sportCodes.map((code) => sportNameByCode(code))
  const content = !alreadyNamesSport && sportNames.length
    ? `${reply}（${sportNames.join('、')}）`
    : reply
  void sendAgentMessage(content, sportCodes)
}

function openAgentSportPicker(reply: string) {
  agentPendingQuickReply.value = reply
  const contextualSports = sportCodesFromAgentContext(reply)
  agentSelectedSportCodes.value = contextualSports.length
    ? contextualSports
    : selectedSport.value
      ? [selectedSport.value]
      : []
  agentSportPickerVisible.value = true
  void nextTick(() => {
    document.getElementById('agent-sport-picker')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

function toggleAgentSport(code: string) {
  if (!code) {
    agentSelectedSportCodes.value = []
    return
  }
  agentSelectedSportCodes.value = agentSelectedSportCodes.value.includes(code)
    ? agentSelectedSportCodes.value.filter((item) => item !== code)
    : [...agentSelectedSportCodes.value, code]
}

function closeAgentSportPicker() {
  agentSportPickerVisible.value = false
  agentPendingQuickReply.value = ''
  agentSelectedSportCodes.value = []
}

function confirmAgentQuickReply() {
  const prompt = agentPendingQuickReply.value.trim()
  if (!prompt) return
  const sportCodes = [...agentSelectedSportCodes.value]
  const sportNames = sportCodes
    .map((code) => sports.value.find((sport) => sport.code === code)?.name)
    .filter(Boolean)
  const content = sportNames.length ? `${prompt}（${sportNames.join('、')}）` : `${prompt}（不限球类）`
  closeAgentSportPicker()
  // 用户在选择器中点了“不限”时，必须保留“全运动”的语义，
  // 不能再回退为首页当前选中的单一运动。
  void sendAgentMessage(content, sportCodes, sportCodes.length === 0)
}

async function sendAgentMessage(
  text = agentInput.value,
  requestedSportCodes: string[] = [],
  allSportsRequested = false
) {
  const content = text.trim()
  if (!content || loading.value) return
  const effectiveSportCodes = allSportsRequested
    ? []
    : requestedSportCodes.length
    ? requestedSportCodes
    : selectedSport.value ? [selectedSport.value] : []
  agentInput.value = ''
  const userMessageId = `u-${Date.now()}`
  agentMessages.value = [
    ...agentMessages.value,
    { id: userMessageId, role: 'user', content }
  ]
  agentQuickReplies.value = []
  agentThinking.value = true
  agentProgressText.value = '正在理解你的需求'
  await nextTick()
  // Position the new turn once. Keeping the same anchor while the answer arrives avoids a second visual jump.
  scrollAgentMessageIntoView(userMessageId, 'smooth', 'start')
  await wrap(async () => {
    let result: AgentChatResponse | undefined
    let streamError = ''
    await streamApi('/agent/chat/stream', {
      method: 'POST',
      body: JSON.stringify({
        conversationId: agentConversationId.value,
        message: content,
        sportCode: effectiveSportCodes.length === 1 ? effectiveSportCodes[0] : '',
        sportCodes: effectiveSportCodes,
        city: placeQuery.city,
        lng: placeQuery.lng,
        lat: placeQuery.lat,
        allSportsRequested
      })
    }, (event, data) => {
      if (event === 'conversation') {
        const payload = data as { conversationId?: number }
        if (payload.conversationId) agentConversationId.value = payload.conversationId
      } else if (event === 'stage') {
        const payload = data as { message?: string }
        if (payload.message) agentProgressText.value = payload.message
      } else if (event === 'done') {
        result = data as AgentChatResponse
      } else if (event === 'error') {
        const payload = data as { message?: string }
        streamError = payload.message || 'AI 助手请求失败，请稍后重试'
      }
    })
    if (streamError) throw new Error(streamError)
    if (!result) throw new Error('AI 助手响应未完成，请稍后重试')
    agentConversationId.value = result.conversationId
    agentQuickReplies.value = agentNextQuickReplies(result.quickReplies, result.cards || [], result.answer)
    agentAiEnabled.value = result.aiEnabled
    agentMessages.value = [
      ...agentMessages.value,
      {
        id: `a-${Date.now()}`,
        role: 'assistant',
        content: result.answer,
        cards: result.cards || []
      }
    ]
    agentThinking.value = false
    agentProgressText.value = ''
  })
  if (agentThinking.value) {
    agentThinking.value = false
  }
  agentProgressText.value = ''
  if (!agentQuickReplies.value.length) {
    syncAgentQuickRepliesFromMessages()
  }
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderAgentContent(content: string) {
  const inline = (value: string) => value.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  const lines = escapeHtml(stripInternalAgentFields(content)).split(/\r?\n/)
  const html: string[] = []
  let orderedItems: string[] = []
  let unorderedItems: string[] = []
  const flushLists = () => {
    if (orderedItems.length) {
      html.push(`<ol class="agent-answer-list">${orderedItems.map((item) => `<li>${inline(item)}</li>`).join('')}</ol>`)
      orderedItems = []
    }
    if (unorderedItems.length) {
      html.push(`<ul class="agent-answer-list">${unorderedItems.map((item) => `<li>${inline(item)}</li>`).join('')}</ul>`)
      unorderedItems = []
    }
  }
  lines.forEach((line) => {
    const ordered = line.match(/^\s*\d+[.、]\s*(.+)$/)
    if (ordered) {
      unorderedItems.length && flushLists()
      orderedItems.push(ordered[1])
      return
    }
    const unordered = line.match(/^\s*[-•]\s*(.+)$/)
    if (unordered) {
      orderedItems.length && flushLists()
      unorderedItems.push(unordered[1])
      return
    }
    flushLists()
    if (!line.trim()) {
      if (html.length && html[html.length - 1] !== '<div class="agent-answer-gap"></div>') {
        html.push('<div class="agent-answer-gap"></div>')
      }
      return
    }
    html.push(`<div class="agent-answer-line">${inline(line)}</div>`)
  })
  flushLists()
  return html.join('')
}

function stripInternalAgentFields(content: string) {
  return content
    .replace(/\s*[（(]?\s*placeRank\s*=\s*\d+\s*[）)]?\s*[：:，,]?\s*/gi, '')
    .replace(/placeRank\s*=\s*\d+\s*[：:，,]?\s*/gi, '')
    .replace(/\s*\(\s*placeRank\s*\)/gi, '')
}

function agentCardTags(card: AgentCard) {
  return (card.tags || []).filter(Boolean).slice(0, 3)
}

function agentCardActionLabel(card: AgentCard) {
  if (card.type === 'place') return '看场所'
  if (card.type === 'venue_product') return '看团购'
  if (card.type === 'activity') return '加入'
  if (card.type === 'equipment') return '看装备'
  if (card.type === 'seckill') return '去抢购'
  return '查看'
}

function agentCardTypeLabel(card: AgentCard) {
  if (card.type === 'place') return '场所'
  if (card.type === 'activity') return '约球'
  if (card.type === 'venue_product') return '团购'
  if (card.type === 'seckill') return '秒杀'
  return '装备'
}

function agentSummary(content: string) {
  const text = content.replace(/\s+/g, ' ').trim()
  if (text.length <= 42) return text
  const firstSentence = text.match(/^.*?[。！？.!?]/)?.[0]
  if (firstSentence && firstSentence.length <= 48) return firstSentence
  return `${text.slice(0, 40)}...`
}

function agentPlaceKey(card: AgentCard) {
  const meta = card.meta || {}
  const sportCode = String(meta.sportCode || card.action?.payload?.sportCode || '')
  const placeRank = String(meta.placeRank || card.action?.payload?.placeRank || '')
  return sportCode && placeRank ? `${sportCode}:${placeRank}` : ''
}

function agentPlaceBundles(cards: AgentCard[] = []): AgentPlaceBundle[] {
  const dealsByPlace = new Map<string, AgentCard[]>()
  cards
    .filter((card) => card.type === 'venue_product')
    .forEach((card) => {
      const key = agentPlaceKey(card)
      if (!key) return
      dealsByPlace.set(key, [...(dealsByPlace.get(key) || []), card])
    })
  return cards
    .filter((card) => card.type === 'place')
    .map((place) => ({
      place,
      deal: dealsByPlace.get(agentPlaceKey(place))?.[0]
    }))
}

function agentStandaloneCards(cards: AgentCard[] = []) {
  const placeKeys = new Set(cards.filter((card) => card.type === 'place').map(agentPlaceKey).filter(Boolean))
  return cards.filter((card) => {
    if (card.type === 'place') return false
    if (card.type !== 'venue_product') return true
    const key = agentPlaceKey(card)
    return !key || !placeKeys.has(key)
  })
}

function agentStandaloneSectionTitle(cards: AgentCard[] = []) {
  const standaloneCards = agentStandaloneCards(cards)
  return standaloneCards.length > 0 && standaloneCards.every((card) => card.type === 'activity')
    ? '可加入的约球'
    : '更多推荐'
}

function agentVenueProductId(card: AgentCard) {
  const value = Number(card.action?.id || card.meta?.id)
  return Number.isFinite(value) ? value : null
}

function agentBookingVenueName(booking: AgentVenueBooking) {
  return booking.place?.title || String(booking.product.meta?.placeTitle || booking.product.meta?.venueName || '')
}

function agentBookingPlaceId(booking: AgentVenueBooking) {
  return booking.place?.action?.id || String(booking.product.meta?.placeId || '')
}

function agentBookingCity(booking: AgentVenueBooking) {
  return String(booking.place?.meta?.city || booking.product.meta?.city || placeQuery.city || '西安市')
}

function agentInventoryText(inventory: VenueInventory) {
  return `${inventory.serviceDate} ${inventory.startTime.slice(0, 5)}-${inventory.endTime.slice(0, 5)}`
}

async function prepareAgentVenueBooking(product: AgentCard, place?: AgentCard) {
  if (!requireLogin('请先登录后确认购买并发起约球')) return
  const productId = agentVenueProductId(product)
  if (productId == null) {
    message.value = '该团购缺少商品信息，暂时无法约球'
    return
  }
  await wrap(async () => {
    const inventories = await api<VenueInventory[]>(`/items/1/${productId}/inventories`)
    const available = inventories.filter((item) => item.purchasable)
    if (!available.length) {
      message.value = '该团购当前没有可购买的时段'
      return
    }
    agentVenueBooking.value = {
      product,
      place,
      inventories: available,
      inventoryId: available[0].id,
      maxPlayers: 4,
      levelRequired: '不限'
    }
  })
}

function closeAgentVenueBooking() {
  if (agentVenueBookingSubmitting.value) return
  agentVenueBooking.value = null
}

async function confirmAgentVenueBooking() {
  const booking = agentVenueBooking.value
  const inventory = agentBookingInventory.value
  const productId = booking ? agentVenueProductId(booking.product) : null
  if (!booking || !inventory || productId == null) return
  const maxPlayers = Number(booking.maxPlayers)
  if (!Number.isInteger(maxPlayers) || maxPlayers < 2 || maxPlayers > 20) {
    message.value = '计划人数请选择 2 至 20 人（含自己）'
    return
  }
  const placeId = agentBookingPlaceId(booking)
  const venueName = agentBookingVenueName(booking)
  if (!placeId || !venueName) {
    message.value = '该团购尚未关联具体场所，无法自动发起约球'
    return
  }
  await wrap(async () => {
    agentVenueBookingSubmitting.value = true
    const result = await api<{ venueOrderId: number; verifyCode: string; activityId: number }>('/social/activities/book-and-create', {
      method: 'POST',
      body: JSON.stringify({
        productId,
        inventoryId: inventory.id,
        placeId,
        placeSource: String(booking.product.meta?.placeSource || 'amap'),
        venueName,
        city: agentBookingCity(booking),
        maxPlayers,
        levelRequired: booking.levelRequired
      })
    })
    ordersLoaded.value = false
    activityScope.value = 'created'
    activities.value = []
    activitiesPage.value = 1
    activitiesTotal.value = 0
    agentVenueBooking.value = null
    message.value = `已购买 ${agentInventoryText(inventory)}，并发起约球活动 #${result.activityId}`
  }).finally(() => {
    agentVenueBookingSubmitting.value = false
  })
}

async function handleAgentCardAction(card: AgentCard) {
  const actionType = card.action?.type
  if (actionType === 'open_place') {
    await openAgentPlace(card)
    return
  }
  if (actionType === 'open_venue_product') {
    await openAgentVenueProduct(card)
    return
  }
  if (actionType === 'open_equipment') {
    await openAgentEquipment(card)
    return
  }
  if (actionType === 'join_activity') {
    const id = Number(card.action?.id)
    if (Number.isFinite(id)) {
      await joinActivity(id)
    }
    return
  }
  message.value = '暂不支持该操作'
}

async function openAgentPlace(card: AgentCard) {
  homeReturnTab.value = 'assistant'
  activeTab.value = 'home'
  venueSaleView.value = null
  venueSaleItems.value = []
  venueSalesPage.value = 1
  venueSalesTotal.value = 0
  if (!places.value.length) {
    await wrap(loadPlaces)
  }
  const id = card.action?.id || String(card.meta?.id || '')
  const existed = places.value.find((place) => place.id === id)
  await selectPlace(existed || placeFromAgentCard(card))
}

function placeFromAgentCard(card: AgentCard): Place {
  const meta = card.meta || {}
  const sportCode = String(meta.sportCode || selectedSport.value || 'badminton')
  return {
    id: card.action?.id || String(meta.id || card.title),
    name: card.title,
    sportCode,
    sportName: String(meta.sportName || sportNameByCode(sportCode)),
    city: String(meta.city || placeQuery.city || '西安市'),
    area: String(meta.area || ''),
    address: card.subtitle || '',
    longitude: Number(meta.lng || placeQuery.lng),
    latitude: Number(meta.lat || placeQuery.lat),
    distanceMeters: Number(meta.distanceMeters || 0),
    coverUrl: card.coverUrl,
    facilities: agentCardTags(card),
    source: 'agent'
  }
}

async function openAgentVenueProduct(card: AgentCard) {
  const id = Number(card.action?.id)
  if (!Number.isFinite(id)) return
  const sportCode = String(card.action?.payload?.sportCode || card.meta?.sportCode || selectedSport.value || '')
  if (sportCode) {
    selectedSport.value = sportCode
  }
  homeReturnTab.value = 'assistant'
  activeTab.value = 'home'
  selectedPlace.value = null
  venueReviews.value = []
  venueSaleView.value = { productType: '', title: '场馆团购', subtitle: 'AI 为你筛选的项目' }
  venueSaleItems.value = []
  venueSalesPage.value = 1
  venueSalesTotal.value = 0
  await wrap(async () => {
    if (!places.value.length) {
      await loadPlaces()
    }
    const detail = await api<VenueItem>(`/items/1/${id}`)
    const mapped = productWithPlaceContext(detail, placeForEquipmentItem(detail))
    venueSaleItems.value = [mapped]
    venueSalesTotal.value = 1
    await nextTick()
    await scrollToRelatedItem('VENUE_PRODUCT', mapped.id)
  })
}

async function openAgentEquipment(card: AgentCard) {
  const id = Number(card.action?.id || card.meta?.productId)
  if (!Number.isFinite(id)) return
  activeTab.value = 'equipment'
  productQuery.keyword = ''
  productQuery.categoryId = ''
  await wrap(async () => {
    const detail = await api<EquipmentItem>(`/items/2/${id}`)
    selectedSport.value = detail.sportCode || selectedSport.value
    await Promise.all([loadEquipmentItems(), loadSeckill()])
    products.value = [detail, ...products.value.filter((item) => item.id !== detail.id)]
    await scrollToRelatedItem('EQUIPMENT', detail.id)
  })
}

async function showSocial() {
  scrollToPageTop()
  refreshActivityWindowIfExpired()
  await switchTab('social')
  await wrap(ensureActivityPlaceOptions)
  await nextTick()
  scrollToPageTop()
}

async function selectPlace(place: Place) {
  selectedPlace.value = place
  placeDetailTab.value = 'deals'
  dealFilter.value = 'all'
  purchaseNotice.value = ''
  await wrap(async () => {
    await Promise.all([loadVenueItemsForPlace(place), loadVenueReviewsForPlace(place)])
  })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function closePlaceDetail() {
  if (relatedBlogReturn.value) {
    await returnToBlogDetail()
    return
  }
  const returnTab = homeReturnTab.value
  selectedPlace.value = null
  venueReviews.value = []
  purchaseNotice.value = ''
  if (returnTab) {
    homeReturnTab.value = null
    activeTab.value = returnTab
    await nextTick()
    document.getElementById('agent-chat-bottom')?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }
}

async function switchPlaceDetailTab(tab: 'deals' | 'reviews') {
  placeDetailTab.value = tab
  if (tab === 'reviews' && selectedPlace.value && !venueReviews.value.length) {
    await wrap(() => loadVenueReviewsForPlace(selectedPlace.value as Place))
  }
}

async function loadVenueReviewsForPlace(place: Place) {
  const sport = place.sportCode || selectedSport.value
  venueReviews.value = await api<VenueReview[]>(`/venues/reviews/${sport}/${placeRankFor(place)}`, {
    headers: locationCityHeader(place.city || placeQuery.city)
  })
}

async function buyVenueItem(product: VenueItem) {
  if (!requireLogin('请先登录后购买场所套餐')) return
  await wrap(async () => {
    buyingVenueItemId.value = product.id
    purchaseNotice.value = ''
    const inventory = await ensureVenueInventory(product)
    if (!inventory) {
      message.value = '该项目暂无可购买时段'
      return
    }
    const created = await api<{ orderId: number; verifyCode: string; order: VenueOrder }>('/payments', {
      method: 'POST',
      body: JSON.stringify({
        type: 1,
        productId: product.id,
        inventoryId: inventory.id
      })
    })
    ordersLoaded.value = false
    purchaseNotice.value = `购买成功，核销码 ${created.verifyCode}`
    message.value = '购买成功，可在“我的”查看订单'
  }).finally(() => {
    buyingVenueItemId.value = null
  })
}

async function addCart(product: EquipmentItem) {
  if (!requireLogin('请先登录后加入购物车')) return
  await wrap(async () => {
    await api('/cart', {
      method: 'POST',
      body: JSON.stringify({ type: 2, productId: product.id, quantity: 1 })
    })
    cartCount.value += 1
    cartLoaded.value = false
  }, '已加入购物车')
}

async function addVenueCart(product: VenueItem) {
  if (!requireLogin('请先登录后加入购物车')) return
  await wrap(async () => {
    const inventory = await ensureVenueInventory(product)
    if (!inventory) {
      message.value = '该项目暂无可购买时段'
      return
    }
    await api('/cart', {
      method: 'POST',
      body: JSON.stringify({ type: 1, productId: product.id, inventoryId: inventory.id, quantity: 1 })
    })
    cartCount.value += 1
    cartLoaded.value = false
  }, '已加入购物车')
}

async function buyEquipmentNow(product: EquipmentItem) {
  if (!requireLogin('请先登录后购买装备')) return
  await wrap(async () => {
    await api('/payments', {
      method: 'POST',
      body: JSON.stringify({ type: 2, productId: product.id, quantity: 1, address: profileAddress() })
    })
    ordersLoaded.value = false
  }, '装备订单已支付')
}

function profileAddress() {
  return userProfile.value?.city ? `${userProfile.value.city} 到店自提` : '到店自提'
}

async function checkoutCart() {
  if (!requireLogin('请先登录后结算购物车')) return
  if (!cartItems.value.length) {
    message.value = '购物车为空'
    return
  }
  await wrap(async () => {
    await api<{ orderId: number }>('/payments/cart', {
      method: 'POST',
      body: JSON.stringify({ address: profileAddress() })
    })
    cartItems.value = []
    cartCount.value = 0
    cartLoaded.value = true
    ordersLoaded.value = false
  }, '装备订单已支付')
}

async function showProfileCart() {
  if (!requireLogin('请先登录后查看购物车')) return
  activeTab.value = 'profile'
  profileMode.value = 'me'
  viewedUserProfile.value = null
  await openProfileView('cart')
}

async function removeCartItem(item: CartItem) {
  await wrap(async () => {
    await api(`/cart/${item.type}/${item.id}`, { method: 'DELETE' })
    cartItems.value = cartItems.value.filter((record) => !(record.type === item.type && record.id === item.id))
    cartCount.value = cartItems.value.reduce((sum, record) => sum + record.quantity, 0)
    cartLoaded.value = true
  }, '已移出购物车')
}

async function updateCartQuantity(item: CartItem, quantity: number) {
  const nextQuantity = Math.min(Math.max(quantity, 1), 99)
  if (nextQuantity === item.quantity) return
  await wrap(async () => {
    await api(`/cart/${item.type}/${item.id}`, {
      method: 'PATCH',
      body: JSON.stringify({ quantity: nextQuantity })
    })
    cartItems.value = cartItems.value.map((record) => {
      if (record.type !== item.type || record.id !== item.id) return record
      return {
        ...record,
        quantity: nextQuantity,
        amount: Number(record.price) * nextQuantity
      }
    })
    cartCount.value = cartItems.value.reduce((sum, record) => sum + record.quantity, 0)
    cartLoaded.value = true
  }, '数量已更新')
}

async function submitSeckill(activityId: number) {
  if (!requireLogin('请先登录后参与秒杀')) return
  await wrap(async () => {
    const result = await api<{ orderId: number }>(`/seckill/2/${activityId}`, { method: 'POST' })
    message.value = `抢购请求已进入队列，订单号 ${result.orderId}`
    ordersLoaded.value = false
    await loadSeckill()
  })
}

async function createActivity() {
  if (!requireLogin('请先登录后发起约球')) return
  if (!activityForm.sportCode) {
    message.value = '请选择约球运动类型'
    return
  }
  if (!activityForm.placeId) {
    message.value = '请先从附近场所下拉列表选择一个场所'
    return
  }
  const maxPlayers = Number(activityForm.maxPlayers)
  if (!Number.isInteger(maxPlayers) || maxPlayers < 2 || maxPlayers > 20) {
    message.value = '计划人数请选择 2 至 20 人（含自己）'
    return
  }
  await wrap(async () => {
    await api('/social/activities', {
      method: 'POST',
      body: JSON.stringify({
        ...activityForm,
        sportCode: activityForm.sportCode,
        city: activityForm.city || locationLabel.value,
        venueId: null,
        maxPlayers,
        startTime: `${activityForm.startTime}:00`,
        endTime: `${activityForm.endTime}:00`
      })
    })
    // 发起成功后切到“自己发起”，让新活动无需等待列表刷新即可出现。
    activityScope.value = 'created'
    activities.value = []
    activitiesPage.value = 1
    activitiesTotal.value = 0
    await loadActivities()
  }, '约球活动已发布')
}

async function joinActivity(id: number) {
  if (!requireLogin('请先登录后加入活动')) return
  await wrap(async () => {
    await api(`/social/activities/${id}/join`, { method: 'POST' })
    await loadActivities()
  }, '已加入活动')
}

async function usePlaceForActivity(place: Place) {
  applyActivityPlace(place)
  await switchTab('social')
  await nextTick()
  document.getElementById('activity-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function placeDomId(place: Place) {
  return `place-${place.id}`
}

function openAmap(place: Place) {
  if (!place.longitude || !place.latitude) return
  const url = `https://uri.amap.com/marker?position=${place.longitude},${place.latitude}&name=${encodeURIComponent(place.name)}`
  window.open(url, '_blank', 'noopener,noreferrer')
}

function yuan(value: number) {
  return `￥${Number(value).toFixed(2)}`
}

function distance(value?: number) {
  if (!value) return ''
  return value > 1000 ? `${(value / 1000).toFixed(1)}km` : `${Math.round(value)}m`
}

function maskedPhone(phone?: string) {
  if (!phone) return '未绑定手机号'
  return phone.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

function formatDateTime(value?: string) {
  if (!value) return '刚刚加入'
  return value.replace('T', ' ').slice(0, 16)
}

function reviewImages(review: VenueReview) {
  const images = (review.imageUrls || '')
    .split(',')
    .map((url) => url.trim())
    .filter(Boolean)
  if (images.length) return images
  return selectedPlace.value?.coverUrl ? [selectedPlace.value.coverUrl] : []
}

function reviewDate(value?: string) {
  return formatDateTime(value).slice(5, 10)
}

function saleVenueItems(place: Place) {
  return venueItems.value[place.id] || []
}

function hasLoadedVenueItems(place: Place) {
  return Object.prototype.hasOwnProperty.call(venueItems.value, place.id)
}

async function ensureVenueInventories(product: VenueItem) {
  if (Object.prototype.hasOwnProperty.call(inventoriesByVenueItem.value, product.id)) {
    return inventoriesByVenueItem.value[product.id] || []
  }
  const inventories = await api<VenueInventory[]>(`/items/1/${product.id}/inventories`)
  inventoriesByVenueItem.value = {
    ...inventoriesByVenueItem.value,
    [product.id]: inventories
  }
  return inventories
}

async function ensureVenueInventory(product: VenueItem) {
  const inventories = await ensureVenueInventories(product)
  return inventories.find((item) => item.purchasable) || null
}

function firstInventory(product: VenueItem) {
  return (inventoriesByVenueItem.value[product.id] || []).find((item) => item.purchasable)
}

function inventoryText(product: VenueItem) {
  const inventory = firstInventory(product)
  if (!inventory) return product.productTypeName
  return `${inventory.serviceDate.slice(5)} ${inventory.startTime.slice(0, 5)}-${inventory.endTime.slice(0, 5)}`
}

function hasDiscountPrice(item: { price: number; originalPrice?: number }) {
  return item.originalPrice != null && Number(item.originalPrice) > Number(item.price)
}

function typeClass(productType: string) {
  return {
    'type-time': productType === 'TIME_PACKAGE',
    'type-slot': productType === 'COURT_SLOT',
    'type-coach': productType === 'COACH_LESSON'
  }
}

onMounted(async () => {
  if ('scrollRestoration' in history) {
    history.scrollRestoration = 'manual'
  }
  scrollToPageTop()
  resetHeaderVisibility()
  window.addEventListener('scroll', handleWindowScroll, { passive: true })
  refreshActivityWindowIfExpired(true)
  try {
    await loadSports()
  } catch {
    sports.value = fallbackSports
  }
  // 定位可能等待数秒；社区预取不依赖定位，两个任务并行避免首次切换仍在等网络。
  const locationRequest = useCurrentLocation()
  preloadRecommendedBlogs()
  await locationRequest
  if (!places.value.length) {
    await loadCurrentTab()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleWindowScroll)
})
</script>

<template>
  <div class="phone-shell">
    <section v-if="authPageVisible" class="auth-page">
      <div class="auth-topbar">
        <button class="auth-back" type="button" aria-label="返回" @click="closeAuthPage">
          <ChevronLeft :size="21" />
        </button>
        <span>登录/注册</span>
      </div>

      <div class="auth-hero">
        <div class="auth-mark">
          <img src="/Icon.png?v=20260704" alt="约个球" />
        </div>
        <h1>约个球</h1>
        <p>上球搭子，约个球</p>
      </div>

      <section class="auth-panel">
        <div class="auth-mode-tabs">
          <button :class="{ active: authMode === 'code' }" type="button" @click="authMode = 'code'">验证码登录</button>
          <button :class="{ active: authMode === 'password' }" type="button" @click="authMode = 'password'">密码登录</button>
        </div>

        <template v-if="authMode === 'code'">
          <label class="auth-field">
            <span>邮箱</span>
            <div class="auth-input">
              <Mail :size="18" />
              <input v-model="loginForm.email" type="email" inputmode="email" placeholder="请输入邮箱" />
            </div>
          </label>

          <label class="auth-field">
            <span>验证码</span>
            <div class="auth-code-row">
              <div class="auth-input">
                <MessageCircle :size="18" />
                <input v-model="loginForm.code" inputmode="numeric" maxlength="6" placeholder="请输入验证码" />
              </div>
              <button class="code-button" @click="sendLoginCode" :disabled="loading || codeCountdown > 0 || !loginForm.email.trim()">
                {{ codeCountdown > 0 ? `${codeCountdown}s` : '获取验证码' }}
              </button>
            </div>
          </label>
        </template>

        <template v-else>
          <label class="auth-field">
            <span>账号</span>
            <div class="auth-input">
              <Mail :size="18" />
              <input v-model="passwordLoginForm.account" placeholder="邮箱 / 用户名 / 手机号" />
            </div>
          </label>

          <label class="auth-field">
            <span>密码</span>
            <div class="auth-input password-input">
              <KeyRound :size="18" />
              <input v-model="passwordLoginForm.password" :type="passwordVisible ? 'text' : 'password'" placeholder="请输入密码" />
              <button
                class="password-toggle"
                type="button"
                :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                @click="passwordVisible = !passwordVisible"
              >
                <EyeOff v-if="passwordVisible" :size="18" />
                <Eye v-else :size="18" />
              </button>
            </div>
          </label>
        </template>

        <button class="auth-submit" @click="login" :disabled="loading">
          <LogIn :size="18" />
          {{ authMode === 'code' ? '登录/注册' : '登录' }}
        </button>
      </section>

      <div v-if="message" class="toast auth-toast" role="status">
        <span>{{ message }}</span>
        <button class="toast-close" type="button" aria-label="关闭提示" @click="closeMessage">
          <X :size="14" />
        </button>
      </div>
    </section>

    <template v-else>
    <header v-if="showPhoneHeader" class="phone-header" :class="{ compact: !showDiscoveryHeader, hidden: !headerVisible }">
      <div class="header-row">
        <div class="location-cluster">
          <button class="location-button" @click="useCurrentLocation" :disabled="locating">
            <LocateFixed :size="17" />
            <span>{{ locating ? '定位中' : locationLabel }}</span>
          </button>
          <span class="location-detail">{{ preciseLocationLabel }}</span>
        </div>
        <span v-if="loggedIn" class="login-chip"><ShieldCheck :size="15" /> 已登录</span>
        <button v-else class="login-chip muted" @click="openAuthPage(activeTab)"><LogIn :size="15" /> 去登录</button>
      </div>

      <div v-if="activeTab === 'assistant'" class="agent-hero header-agent-hero">
        <div class="agent-orb">
          <Bot :size="18" />
        </div>
        <div class="agent-hero-copy">
          <span>{{ agentAiEnabled ? 'DashScope 已接入' : '本地工具模式' }}</span>
          <strong>约个球助手</strong>
          <p>场地 · 约球 · 装备</p>
        </div>
        <div class="agent-hero-actions">
          <button type="button" @click="openAgentHistory">
            <MessageCircle :size="14" />
            历史
          </button>
          <button type="button" class="primary" @click="startNewAgentConversation">
            <PenLine :size="14" />
            新对话
          </button>
        </div>
      </div>

      <div v-if="showDiscoveryHeader" class="search-bar">
        <Search :size="18" />
        <input v-model="headerSearchKeyword" :placeholder="headerSearchPlaceholder" @keyup.enter="searchPlaces" />
        <button @click="searchPlaces">搜索</button>
      </div>

      <div v-if="showDiscoveryHeader" class="sport-scroll">
        <template v-if="activeTab === 'seckill'">
          <button type="button" :class="{ active: blogChannel === 'follow' }" @click="selectBlogChannel('follow')">关注</button>
          <button type="button" :class="{ active: blogChannel === 'recommend' }" @click="selectBlogChannel('recommend')">推荐</button>
          <button
            v-for="sport in sports"
            :key="sport.code"
            type="button"
            :class="{ active: blogChannel === 'sport' && blogSport === sport.code }"
            @click="selectBlogChannel('sport', sport.code)"
          >
            {{ sport.name }}
          </button>
        </template>
        <template v-else>
          <button :class="{ active: !selectedSport }" @click="changeSport('')">全部</button>
          <button
            v-for="sport in sports"
            :key="sport.code"
            :class="{ active: selectedSport === sport.code }"
            @click="changeSport(sport.code)"
          >
            {{ sport.name }}
          </button>
        </template>
      </div>

      <div v-if="showCategoryHeader" class="category-scroll header-category-row">
        <template v-if="activeTab === 'equipment'">
          <button :class="{ active: !productQuery.categoryId }" @click="selectEquipmentItemCategory('')">全部分类</button>
          <button
            v-for="category in categories"
            :key="category.id"
            :class="{ active: productQuery.categoryId === String(category.id) }"
            @click="selectEquipmentItemCategory(String(category.id))"
          >
            {{ category.name }}
          </button>
        </template>
      </div>
    </header>

    <main
      class="phone-main"
      :class="{
        'header-full': showPhoneHeader && showDiscoveryHeader && !showCategoryHeader,
        'header-with-categories': showPhoneHeader && showCategoryHeader,
        'header-agent': showPhoneHeader && activeTab === 'assistant',
        'header-compact': showPhoneHeader && !showDiscoveryHeader && activeTab !== 'assistant'
      }"
    >
      <div
        v-if="message"
        class="toast floating-toast"
        :class="{
          'below-header-full': headerVisible && showPhoneHeader && showDiscoveryHeader && !showCategoryHeader,
          'below-header-categories': headerVisible && showPhoneHeader && showCategoryHeader,
          'below-header-agent': headerVisible && showPhoneHeader && activeTab === 'assistant',
          'below-header-compact': headerVisible && showPhoneHeader && !showDiscoveryHeader && activeTab !== 'assistant'
        }"
        role="status"
        aria-live="polite"
      >
        <span>{{ message }}</span>
        <button class="toast-close" type="button" aria-label="关闭提示" @click="closeMessage">
          <X :size="14" />
        </button>
      </div>
      <section v-if="blogComposerVisible" class="page-stack blog-publish-page">
        <div class="section-title publish-title">
          <div>
            <span>分享体验</span>
            <strong>发动态</strong>
          </div>
          <div class="title-actions">
            <button class="ghost" type="button" @click="closeBlogPublisher"><ChevronLeft :size="16" /> 返回</button>
            <button class="primary" type="button" @click="publishBlog" :disabled="loading">
              <Send :size="16" />
              发布
            </button>
          </div>
        </div>

        <section class="publish-panel">
          <label class="form-field">
            <span>运动类型</span>
            <select v-model="blogPublishForm.sportCode" @change="changeBlogPublishSport">
              <option v-for="sport in sports" :key="sport.code" :value="sport.code">{{ sport.name }}</option>
            </select>
          </label>

          <label class="form-field">
            <span>关联内容</span>
            <select v-model="blogPublishForm.relatedKey" @change="changeBlogRelatedOption">
              <option value="">选择要关联的装备或场馆团购</option>
              <option v-for="option in blogRelatedOptions" :key="option.key" :value="option.key">
                {{ option.title }} · {{ option.subtitle }}
              </option>
            </select>
          </label>

          <label class="form-field">
            <span>标题</span>
            <div class="publish-message-input">
              <PenLine :size="17" />
              <input v-model="blogPublishForm.title" maxlength="48" placeholder="给这次体验起一个吸引人的标题" />
            </div>
          </label>

          <label class="form-field">
            <span>正文</span>
            <div class="publish-message-input publish-message-textarea">
              <MessageCircle :size="17" />
              <textarea v-model="blogPublishForm.content" maxlength="500" placeholder="分享你的真实感受、适合的人群，以及值得注意的细节" />
            </div>
          </label>

          <div class="form-field">
            <span>动态图片</span>
            <label class="upload-tile">
              <ImagePlus :size="18" />
              <strong>选择图片</strong>
              <small>支持 jpg / png / webp，最多 9 张</small>
              <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="uploadBlogImages" />
            </label>
            <div v-if="blogPublishForm.imageUrls.length" class="image-preview-grid">
              <button v-for="image in blogPublishForm.imageUrls" :key="image" type="button" @click="removeBlogImage(image)">
                <img :src="image" alt="动态图片" @error="imageFallback" />
                <span><X :size="13" /></span>
              </button>
            </div>
          </div>

          <article v-if="selectedBlogRelatedOption()" class="publish-related-preview">
            <img
              :src="selectedBlogRelatedOption()?.coverUrl || FALLBACK_IMAGE"
              :alt="selectedBlogRelatedOption()?.title"
              @error="imageFallback"
            />
            <div>
              <span>{{ selectedBlogRelatedOption()?.subtitle }}</span>
              <strong>{{ selectedBlogRelatedOption()?.title }}</strong>
              <em v-if="selectedBlogRelatedOption()?.price">{{ yuan(selectedBlogRelatedOption()?.price || 0) }}</em>
            </div>
          </article>
        </section>
      </section>

      <section v-else-if="selectedBlog" class="page-stack blog-detail-page">
        <div class="section-title">
          <div>
            <span>{{ sportNameByCode(selectedBlog.sportCode) }}</span>
            <strong>动态详情</strong>
          </div>
          <button class="ghost" @click="closeBlogDetail"><ChevronLeft :size="16" /> 返回</button>
        </div>

        <article class="blog-detail-card">
          <div class="blog-detail-author">
            <button class="profile-link" type="button" @click="openUserProfile(selectedBlog.userId)">
              <img :src="selectedBlog.avatar || FALLBACK_AVATAR" :alt="selectedBlog.nickname" @error="imageFallback($event, FALLBACK_AVATAR)" />
              <span>{{ selectedBlog.nickname }}</span>
            </button>
            <button
              v-if="!loggedIn || userProfile?.id !== selectedBlog.userId"
              type="button"
              class="follow-mini"
              :class="{ active: selectedBlog.followed }"
              @click="toggleBlogFollow(selectedBlog)"
            >
              {{ selectedBlog.followed ? '已关注' : '关注' }}
            </button>
          </div>

          <div class="blog-detail-images" v-if="selectedBlog.images.length">
            <img v-for="image in selectedBlog.images" :key="image" :src="image" :alt="selectedBlog.title" @error="imageFallback" />
          </div>
          <h2>{{ selectedBlog.title }}</h2>
          <p>{{ selectedBlog.content }}</p>
          <button class="blog-related detail-related" type="button" @click="openBlogRelated(selectedBlog)">
            <ShoppingBag :size="15" />
            <span>{{ selectedBlog.relatedTitle }}</span>
            <strong v-if="selectedBlog.relatedPrice">{{ yuan(selectedBlog.relatedPrice) }}</strong>
          </button>
          <div class="blog-detail-actions">
            <span>{{ formatDateTime(selectedBlog.createdAt) }}</span>
            <div class="blog-detail-action-buttons">
              <button v-if="isOwnBlog(selectedBlog)" type="button" class="delete-mini" @click="deleteBlog(selectedBlog)">
                <Trash2 :size="15" />
                删除
              </button>
              <button type="button" class="like-mini" :class="{ active: selectedBlog.isLiked }" @click="toggleBlogLike(selectedBlog)">
                <Heart :size="17" />
                {{ selectedBlog.liked }}
              </button>
            </div>
          </div>
        </article>
      </section>

      <section v-else-if="activeTab === 'home'" class="page-stack">
        <section v-if="selectedPlace" class="place-detail-page">
          <div class="place-detail-hero">
            <button class="detail-back" type="button" aria-label="返回" @click="closePlaceDetail">
              <ChevronLeft :size="20" />
            </button>
            <img
              :src="selectedPlace.coverUrl || FALLBACK_IMAGE"
              :alt="selectedPlace.name"
              @error="imageFallback"
            />
          </div>

          <section class="place-detail-card">
            <div class="place-title detail-title">
              <h2>{{ selectedPlace.name }}</h2>
              <small>{{ distance(selectedPlace.distanceMeters) }}</small>
            </div>
            <div class="detail-score-row">
              <span>4.4 ★★★★★ · {{ venueReviews.length || 105 }}条评价</span>
              <em>{{ selectedPlace.sportName || activeSport.name }}</em>
            </div>
            <p class="detail-line">营业中 {{ selectedPlace.openHours || '09:00-22:00' }}</p>
            <p class="detail-line">{{ selectedPlace.area || selectedPlace.city }} · {{ selectedPlace.address || selectedPlace.businessArea }}</p>
            <div class="detail-actions">
              <button type="button" @click="usePlaceForActivity(selectedPlace)"><Users :size="16" /> 约球</button>
              <button type="button" @click="openAmap(selectedPlace)"><ExternalLink :size="16" /> 地图</button>
              <a v-if="selectedPlace.tel" class="detail-action" :href="`tel:${selectedPlace.tel}`"><Phone :size="16" /> 电话</a>
            </div>
          </section>

          <div class="place-detail-tabs">
            <button type="button" :class="{ active: placeDetailTab === 'deals' }" @click="switchPlaceDetailTab('deals')">团购</button>
            <button type="button" :class="{ active: placeDetailTab === 'reviews' }" @click="switchPlaceDetailTab('reviews')">评价</button>
          </div>

          <section v-if="placeDetailTab === 'deals'" class="place-detail-section">
            <div class="section-title compact-title">
              <div>
                <span>可购买</span>
                <strong>场馆团购</strong>
              </div>
            </div>
            <div class="category-scroll deal-filter-row">
              <button type="button" :class="{ active: dealFilter === 'all' }" @click="dealFilter = 'all'">全部</button>
              <button type="button" :class="{ active: dealFilter === 'discount' }" @click="dealFilter = 'discount'">特价</button>
            </div>
            <div v-if="purchaseNotice" class="purchase-notice">{{ purchaseNotice }}</div>
            <div v-if="!hasLoadedVenueItems(selectedPlace)" class="empty-box">正在加载该场馆售卖项目</div>
            <div v-else-if="!selectedPlaceVenueItems.length" class="empty-box">该场馆暂未配置线上售卖项目</div>
            <article v-for="item in selectedPlaceVenueItems" :key="item.id" class="detail-deal-row">
              <img
                :src="item.coverUrl || selectedPlace.coverUrl || FALLBACK_IMAGE"
                :alt="item.title"
                loading="lazy"
                decoding="async"
                @error="imageFallback"
              />
              <div class="detail-deal-main">
                <span class="service-type" :class="typeClass(item.productType)">{{ item.productTypeName }}</span>
                <h3>{{ item.title }}</h3>
                <p>{{ inventoryText(item) }} · {{ item.purchasable ? '可购买' : '已售罄' }}</p>
                <small>{{ item.useRule }}</small>
                <div class="detail-price-line">
                  <strong>{{ yuan(item.price) }}</strong>
                  <span v-if="hasDiscountPrice(item)">{{ yuan(item.originalPrice || 0) }}</span>
                </div>
              </div>
              <div class="dual-action">
                <button type="button" @click="addVenueCart(item)" :disabled="!item.purchasable">加购</button>
                <button class="primary pill-buy" @click="buyVenueItem(item)" :disabled="buyingVenueItemId === item.id || !item.purchasable">
                  {{ buyingVenueItemId === item.id ? '购买中' : '购买' }}
                </button>
              </div>
            </article>
          </section>

          <section v-else class="place-detail-section review-section">
            <div class="review-score-card">
              <strong>4.4</strong>
              <div>
                <h3>综合评分</h3>
                <p>环境很好 · 服务热情 · 场地赞</p>
              </div>
            </div>
            <div v-if="!venueReviews.length" class="empty-box">暂无评价</div>
            <article v-for="review in venueReviews" :key="review.id" class="review-row">
              <img class="review-avatar" :src="review.avatar" :alt="review.nickname" loading="lazy" decoding="async" @error="imageFallback($event, FALLBACK_AVATAR)" />
              <div>
                <div class="review-head">
                  <h3>{{ review.nickname }}</h3>
                  <small>{{ reviewDate(review.createdAt) }}</small>
                </div>
                <p class="review-stars">{{ '★★★★★'.slice(0, Math.round(review.rating || 5)) }} 超赞</p>
                <p>{{ review.content }}</p>
                <div v-if="reviewImages(review).length" class="review-images">
                  <img v-for="image in reviewImages(review).slice(0, 3)" :key="image" :src="image" :alt="review.nickname" loading="lazy" decoding="async" @error="imageFallback" />
                </div>
              </div>
            </article>
          </section>
        </section>

        <template v-else-if="!venueSaleView">
        <section class="quick-grid">
          <button @click="openVenueSalePage('', '附近可订', '数据库中已配置的可售场所项目')"><MapPin :size="20" /> 附近可订</button>
          <button @click="openVenueSalePage('TIME_PACKAGE', '特价畅打', '低峰畅打、多人练球、限时套餐')"><Zap :size="20" /> 特价畅打</button>
          <button @click="openVenueSalePage('COACH_LESSON', '私教课', '一对一体验课与小班训练')"><Activity :size="20" /> 私教课</button>
          <button @click="showSocial"><Users :size="20" /> 同城搭子</button>
        </section>

        <section id="nearby-places" class="nearby-section">
          <div class="section-title">
            <div>
              <span>{{ locationLabel }}</span>
              <strong>附近{{ activeSport.name }}场所</strong>
            </div>
            <button class="ghost" @click="searchPlaces"><Search :size="16" /> 刷新</button>
          </div>

          <template v-for="place in places" :key="place.id">
          <article :id="placeDomId(place)" class="place-card" @click="selectPlace(place)">
            <img :src="place.coverUrl || FALLBACK_IMAGE" :alt="place.name" loading="lazy" decoding="async" @error="imageFallback" />
            <div class="place-info">
              <div class="place-title">
                <h3>{{ place.name }}</h3>
                <small>{{ distance(place.distanceMeters) }}</small>
              </div>
              <p>{{ place.area || place.city }} · {{ place.address || place.businessArea }}</p>
              <div class="tag-row">
                <span v-for="tag in place.facilities.slice(0, 3)" :key="tag">{{ tag }}</span>
              </div>
              <div class="sale-preview" v-if="saleVenueItems(place).length">
                <span v-for="item in saleVenueItems(place).slice(0, 3)" :key="item.id" :class="typeClass(item.productType)">
                  {{ item.productTypeName }} {{ yuan(item.price) }}
                </span>
              </div>
              <div class="place-actions">
                <button @click.stop="selectPlace(place)">购买</button>
                <button @click.stop="usePlaceForActivity(place)">约这里</button>
                <button @click.stop="openAmap(place)"><ExternalLink :size="15" /> 地图</button>
              </div>
            </div>
          </article>

          <section v-if="false" class="buy-panel inline-buy-panel" aria-hidden="true">
            <div class="section-title">
              <div>
                <span>可购买</span>
                <strong>{{ selectedPlace?.name || '' }}</strong>
              </div>
            </div>
            <div v-if="purchaseNotice" class="purchase-notice">{{ purchaseNotice }}</div>
            <div v-if="!hasLoadedVenueItems(place)" class="empty-box">正在加载该场所售卖项目</div>
            <div v-else-if="!saleVenueItems(place).length" class="empty-box">该场所暂未配置线上售卖项目</div>
            <article v-for="item in saleVenueItems(place)" :key="item.id" class="service-row">
              <div>
                <span class="service-type" :class="typeClass(item.productType)">{{ item.productTypeName }}</span>
                <h3>{{ item.title }}</h3>
                <p>{{ inventoryText(item) }} · {{ item.purchasable ? '可购买' : '已售罄' }}</p>
                <small>{{ item.useRule }}</small>
              </div>
              <div class="buy-side">
                <strong>{{ yuan(item.price) }}</strong>
                <span v-if="hasDiscountPrice(item)">{{ yuan(item.originalPrice || 0) }}</span>
                <button class="primary" @click="buyVenueItem(item)" :disabled="buyingVenueItemId === item.id">
                  {{ buyingVenueItemId === item.id ? '购买中' : '购买' }}
                </button>
              </div>
            </article>
          </section>
          </template>
          <div v-if="loadingMore" class="load-more-state">正在加载更多场所</div>
          <div v-else-if="places.length && !hasMorePlaces" class="load-more-state muted-state">
            已经到底了
          </div>
        </section>
        </template>

        <section v-else id="venue-sale-page" class="venue-sale-page">
          <div class="section-title">
            <div>
              <span>{{ activeSport.name }}</span>
              <strong>{{ venueSaleView.title }}</strong>
            </div>
            <button class="ghost" @click="closeVenueSalePage"><ChevronLeft :size="16" /> 返回</button>
          </div>

          <div v-if="!venueSaleItems.length" class="empty-box">暂无可购买项目</div>
          <template v-else>
            <div class="venue-sale-list">
              <article
                v-for="(item, index) in venueSaleItems"
                :id="itemDomId('VENUE_PRODUCT', item.id)"
                :key="item.id"
                class="venue-sale-row"
                :class="{
                  highlighted: isHighlighted('VENUE_PRODUCT', item.id),
                  'sale-spotlight': index === 0
                }"
              >
                <img :src="item.coverUrl || FALLBACK_IMAGE" :alt="item.title" loading="lazy" decoding="async" @error="imageFallback" />
                <div class="venue-sale-content">
                  <div class="sale-row-head">
                    <span class="service-type" :class="typeClass(item.productType)">{{ item.productTypeName }}</span>
                    <small>{{ index === 0 ? '优先推荐' : item.purchasable ? '可购买' : '已售罄' }}</small>
                  </div>
                  <h3>{{ item.title }}</h3>
                  <button class="venue-link" type="button" @click="openVenueSaleItemPlace(item)">
                    {{ item.venueName }}
                  </button>
                  <small>{{ item.description }}</small>
                  <div class="tag-row">
                    <span v-for="tag in item.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
                  </div>
                  <div class="sale-row-bottom">
                    <div class="sale-price-block">
                      <strong>{{ yuan(item.price) }}</strong>
                      <span v-if="hasDiscountPrice(item)">{{ yuan(item.originalPrice || 0) }}</span>
                      <small>{{ index === 0 ? '当前优先为你展示' : '下单后按规则使用' }}</small>
                    </div>
                    <div class="dual-action compact-actions">
                      <button type="button" @click="addVenueCart(item)" :disabled="!item.purchasable">加购</button>
                      <button class="primary" @click="buyVenueItem(item)" :disabled="buyingVenueItemId === item.id || !item.purchasable">
                        {{ buyingVenueItemId === item.id ? '购买中' : '购买' }}
                      </button>
                    </div>
                  </div>
                </div>
              </article>
            </div>
          </template>
          <div v-if="loadingMore" class="load-more-state">正在加载更多可订项目</div>
          <div v-else-if="venueSaleItems.length && !hasMoreVenueSales" class="load-more-state muted-state">已经到底了</div>
        </section>
      </section>

      <section v-else-if="activeTab === 'seckill'" class="page-stack blog-page">
        <div class="section-title">
          <div>
            <span>{{ blogChannel === 'follow' ? '关注动态' : blogChannel === 'sport' ? sportNameByCode(blogSport) : '推荐内容' }}</span>
            <strong>球友社区</strong>
          </div>
          <div class="title-actions">
            <button class="primary" type="button" @click="openBlogPublisher"><PenLine :size="16" /> 发动态</button>
          <button class="ghost" @click="() => loadBlogs(1, false, true)"><Heart :size="16" /> 刷新</button>
          </div>
        </div>

        <div v-if="!blogs.length" class="empty-box">暂无社区动态</div>
        <div v-else class="blog-masonry">
          <article v-for="blog in blogs" :key="blog.id" class="blog-card clickable-card" @click="openBlogDetail(blog)">
            <button class="blog-cover-button" type="button" @click.stop="openBlogDetail(blog)">
              <img
                :src="blog.images[0] || blog.relatedCoverUrl || FALLBACK_IMAGE"
                :alt="blog.title"
                loading="lazy"
                decoding="async"
                @error="imageFallback"
              />
            </button>
            <div class="blog-body">
              <h3>{{ blog.title }}</h3>
              <p>{{ blog.content }}</p>
              <button class="blog-related" type="button" @click.stop="openBlogRelated(blog)">
                <ShoppingBag :size="14" />
                <span>{{ blog.relatedTitle }}</span>
                <strong v-if="blog.relatedPrice">{{ yuan(blog.relatedPrice) }}</strong>
              </button>
              <div class="blog-author-row">
                <button class="blog-author-link" type="button" @click.stop="openUserProfile(blog.userId)">
                  <img :src="blog.avatar || FALLBACK_AVATAR" :alt="blog.nickname" loading="lazy" decoding="async" @error="imageFallback($event, FALLBACK_AVATAR)" />
                  <span>{{ blog.nickname }}</span>
                </button>
                <button
                  v-if="!loggedIn || userProfile?.id !== blog.userId"
                  type="button"
                  class="follow-mini"
                  :class="{ active: blog.followed }"
                  @click.stop="toggleBlogFollow(blog)"
                >
                  {{ blog.followed ? '已关注' : '关注' }}
                </button>
                <button v-else type="button" class="delete-mini icon-only-mini" aria-label="删除动态" @click.stop="deleteBlog(blog)">
                  <Trash2 :size="14" />
                </button>
                <button type="button" class="like-mini" :class="{ active: blog.isLiked }" @click.stop="toggleBlogLike(blog)">
                  <Heart :size="15" />
                  {{ blog.liked }}
                </button>
              </div>
            </div>
          </article>
        </div>
        <div v-if="loadingMore" class="load-more-state">正在加载更多动态</div>
        <div v-else-if="blogs.length && !hasMoreBlogs" class="load-more-state muted-state">已经到底了</div>
      </section>

      <section v-else-if="activeTab === 'social'" class="page-stack">
        <div class="section-title">
          <div>
            <span>{{ activeSport.name }}</span>
            <strong>同城搭子</strong>
          </div>
          <button class="ghost" @click="() => loadSocial()"><Users :size="16" /> 刷新</button>
        </div>
        <div id="activity-form" class="activity-form">
          <label class="form-field wide">
            <span>运动类型</span>
            <select v-model="activityForm.sportCode" @change="changeActivitySport">
              <option v-for="sport in sports" :key="sport.code" :value="sport.code">{{ sport.name }}</option>
            </select>
          </label>
          <div class="activity-inline-row">
            <label class="form-field">
              <span>活动标题</span>
              <input v-model="activityForm.title" placeholder="今晚约一场" />
            </label>
            <label class="form-field">
              <span>场所</span>
              <select v-model="activityForm.placeId" @change="changeActivityPlace">
                <option value="">选择附近场所</option>
                <option v-for="place in places" :key="place.id" :value="place.id">{{ place.name }}</option>
              </select>
            </label>
          </div>
          <div class="time-form-row">
            <label class="form-field">
              <span>开始时间</span>
              <input v-model="activityForm.startTime" type="datetime-local" />
            </label>
            <label class="form-field">
              <span>结束时间</span>
              <input v-model="activityForm.endTime" type="datetime-local" />
            </label>
          </div>
          <div class="activity-inline-row">
            <label class="form-field">
              <span>计划人数（含自己）</span>
              <select v-model.number="activityForm.maxPlayers">
                <option v-for="count in PLAYER_COUNT_OPTIONS" :key="count" :value="count">{{ count }} 人</option>
              </select>
            </label>
            <label class="form-field">
              <span>水平要求</span>
              <select v-model="activityForm.levelRequired">
                <option>不限</option>
                <option>新手友好</option>
                <option>初级以上</option>
                <option>中级对抗</option>
              </select>
            </label>
          </div>
          <button class="primary" @click="createActivity">发起约球</button>
        </div>
        <section class="social-activity-section">
          <div class="section-title compact-title">
            <div>
              <span>约球活动</span>
              <strong>{{ activityScope === 'created' ? '我发起的' : activityScope === 'joined' ? '我加入的' : '他人发起的' }}</strong>
            </div>
          </div>
          <div class="activity-scope-tabs" aria-label="约球活动筛选">
            <button type="button" :class="{ active: activityScope === 'created' }" @click="changeActivityScope('created')">自己发起</button>
            <button type="button" :class="{ active: activityScope === 'others' }" @click="changeActivityScope('others')">他人发起</button>
            <button type="button" :class="{ active: activityScope === 'joined' }" @click="changeActivityScope('joined')">自己加入</button>
          </div>
          <div v-if="!activities.length" class="empty-box compact-empty">
            {{ activityScope === 'created' ? '还没有发起约球，选好场所后发起第一场吧' : activityScope === 'joined' ? '还没有加入约球活动' : '当前筛选下暂无可加入的约球活动' }}
          </div>
          <article v-for="item in activities" :key="item.id" class="activity-row">
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.venueName }} · {{ item.currentPlayers }}/{{ item.maxPlayers }} 人 · {{ item.feeType }}</p>
              <small>{{ item.startTime.replace('T', ' ') }} · {{ item.levelRequired }}</small>
            </div>
            <button v-if="activityScope === 'created' || item.creatorId === userProfile?.id" type="button" disabled>我发起的</button>
            <button v-else-if="activityScope === 'joined'" type="button" disabled>已加入</button>
            <button v-else type="button" @click="joinActivity(item.id)">加入</button>
          </article>
        </section>
        <section class="social-player-section">
          <div class="section-title compact-title">
            <div>
              <span>附近球友</span>
              <strong>找搭子</strong>
            </div>
          </div>
          <article v-for="player in players" :key="player.userId" class="player-row clickable-card" @click="openUserProfile(player.userId)">
            <img :src="player.avatar || FALLBACK_AVATAR" :alt="player.nickname" loading="lazy" decoding="async" @error="imageFallback($event, FALLBACK_AVATAR)" />
            <div>
              <h3>{{ player.nickname }} <span>{{ player.level }}</span></h3>
              <p>{{ player.area }} · {{ player.playStyle }} · {{ player.availableTime }}</p>
            </div>
          </article>
        </section>
        <div v-if="loadingMore" class="load-more-state">正在加载更多约球内容</div>
        <div v-else-if="(players.length || activities.length) && !hasMoreSocial" class="load-more-state muted-state">已经到底了</div>
      </section>

      <section v-else-if="activeTab === 'equipment'" class="page-stack">
        <div class="section-title">
          <div>
            <span>{{ activeSport.name }}</span>
            <strong>装备商城</strong>
          </div>
          <div class="title-actions">
            <button v-if="relatedBlogReturn" class="ghost" type="button" @click="returnToBlogDetail">
              <ChevronLeft :size="16" /> 返回动态
            </button>
            <button class="cart-chip cart-button" @click="showProfileCart" aria-label="查看购物车">
              <ShoppingCart :size="15" />
              <span v-if="cartCount">{{ cartCount }}</span>
            </button>
          </div>
        </div>
        <section v-if="filteredSeckillActivities.length" class="equipment-flash">
          <div class="flash-head">
            <div>
              <span>限时库存</span>
              <strong>秒杀装备</strong>
            </div>
            <button class="ghost" @click="() => loadSeckill()"><Zap :size="15" /> 刷新</button>
          </div>
          <article v-for="activity in filteredSeckillActivities" :key="activity.id" class="deal-row flash-row">
            <img :src="activity.coverUrl || FALLBACK_IMAGE" :alt="activity.productName" loading="lazy" decoding="async" @error="imageFallback" />
            <div>
              <span>{{ activity.categoryName }}</span>
              <h3>{{ activity.productName }}</h3>
              <p>限时特价 · 每人限购 1 件</p>
              <strong>{{ yuan(activity.seckillPrice) }}</strong>
              <em>{{ yuan(activity.originalPrice) }}</em>
            </div>
            <button class="primary" @click="submitSeckill(activity.id)" :disabled="!activity.purchasable">抢购</button>
          </article>
        </section>
        <article
          v-for="product in products"
          :id="itemDomId('EQUIPMENT', product.id)"
          :key="product.id"
          class="product-row"
          :class="{ highlighted: isHighlighted('EQUIPMENT', product.id) }"
        >
          <img :src="product.coverUrl || FALLBACK_IMAGE" :alt="product.name" loading="lazy" decoding="async" @error="imageFallback" />
          <div>
            <span>{{ product.brand }} · {{ product.categoryName }}</span>
            <h3>{{ product.name }}</h3>
            <p>{{ product.description }}</p>
            <strong>{{ yuan(product.price) }}</strong>
          </div>
          <div class="dual-action product-actions">
            <button @click="addCart(product)">加购</button>
            <button class="primary" @click="buyEquipmentNow(product)">抢购</button>
          </div>
        </article>
        <div v-if="loadingMore" class="load-more-state">正在加载更多装备</div>
        <div v-else-if="products.length && !hasMoreEquipmentItems" class="load-more-state muted-state">已经到底了</div>
      </section>

      <section v-else-if="activeTab === 'assistant'" class="page-stack agent-page">
        <div v-if="false" class="agent-hero" :class="{ hidden: !headerVisible }">
          <div class="agent-orb">
            <Bot :size="18" />
          </div>
          <div class="agent-hero-copy">
            <span>{{ agentAiEnabled ? 'DashScope 已接入' : '本地工具模式' }}</span>
            <strong>约个球助手</strong>
            <p>场地 · 约球 · 装备</p>
          </div>
          <div class="agent-hero-actions">
            <button type="button" @click="openAgentHistory">
              <MessageCircle :size="14" />
              历史
            </button>
            <button type="button" class="primary" @click="startNewAgentConversation">
              <PenLine :size="14" />
              新对话
            </button>
          </div>
        </div>

        <section class="agent-chat-panel" aria-label="AI 助手聊天记录">
          <article
            v-for="item in agentMessages"
            :id="`agent-message-${item.id}`"
            :key="item.id"
            class="agent-message"
            :class="item.role"
          >
            <div class="agent-bubble">
              <div v-if="item.role === 'assistant'" class="agent-rich-text" v-html="renderAgentContent(item.content)"></div>
              <p v-else>{{ item.content }}</p>
            </div>
            <template v-if="item.cards?.length">
              <section v-if="agentPlaceBundles(item.cards).length" class="agent-result-section">
                <div class="agent-section-title">
                  <strong>场所推荐</strong>
                  <span>横滑查看更多</span>
                </div>
                <div class="agent-card-carousel">
                  <article
                    v-for="bundle in agentPlaceBundles(item.cards)"
                    :key="`place-${bundle.place.action?.id || bundle.place.title}`"
                    class="agent-place-card"
                  >
                    <button class="agent-place-main" type="button" @click="handleAgentCardAction(bundle.place)">
                      <img v-if="bundle.place.coverUrl" :src="bundle.place.coverUrl" :alt="bundle.place.title" loading="lazy" decoding="async" @error="imageFallback" />
                      <div>
                        <span>场所</span>
                        <h3>{{ bundle.place.title }}</h3>
                        <p>{{ bundle.place.subtitle }}</p>
                        <div class="tag-row" v-if="agentCardTags(bundle.place).length">
                          <span v-for="tag in agentCardTags(bundle.place)" :key="tag">{{ tag }}</span>
                        </div>
                      </div>
                    </button>
                    <div class="agent-place-action-row">
                      <button type="button" @click="handleAgentCardAction(bundle.place)">看场所</button>
                    </div>
                    <div v-if="bundle.deal" class="agent-attached-deal">
                      <button class="agent-attached-deal-main" type="button" @click="handleAgentCardAction(bundle.deal)">
                      <div>
                        <span>可买团购</span>
                        <strong>{{ bundle.deal.title }}</strong>
                        <p>{{ bundle.deal.subtitle }}</p>
                      </div>
                      <em v-if="bundle.deal.price">{{ bundle.deal.price }}</em>
                      </button>
                      <div class="agent-deal-actions">
                        <button type="button" @click="handleAgentCardAction(bundle.deal)">看团购</button>
                        <button class="primary" type="button" @click="prepareAgentVenueBooking(bundle.deal, bundle.place)">约球</button>
                      </div>
                    </div>
                    <div v-else class="agent-attached-empty">暂无在线团购，先看看场所详情</div>
                  </article>
                </div>
              </section>

              <section v-if="agentStandaloneCards(item.cards).length" class="agent-result-section">
                <div class="agent-section-title">
                  <strong>{{ agentStandaloneSectionTitle(item.cards) }}</strong>
                  <span v-if="agentStandaloneCards(item.cards).length > 1">横滑查看更多</span>
                </div>
                <div class="agent-card-carousel">
                  <article
                    v-for="card in agentStandaloneCards(item.cards)"
                    :key="`${card.type}-${card.action?.id || card.title}`"
                    class="agent-result-card"
                    :class="{
                      'without-cover': !card.coverUrl,
                      'activity-card': card.type === 'activity',
                      'single-card': agentStandaloneCards(item.cards).length === 1
                    }"
                  >
                    <img v-if="card.coverUrl" :src="card.coverUrl" :alt="card.title" loading="lazy" decoding="async" @error="imageFallback" />
                    <div class="agent-card-content">
                      <div class="agent-card-kicker">
                        <Users v-if="card.type === 'activity'" :size="14" />
                        <span>{{ agentCardTypeLabel(card) }}</span>
                      </div>
                      <h3>{{ card.title }}</h3>
                      <p>{{ card.subtitle }}</p>
                      <div class="tag-row" v-if="agentCardTags(card).length">
                        <span v-for="tag in agentCardTags(card)" :key="tag">{{ tag }}</span>
                      </div>
                    </div>
                    <div class="agent-card-side">
                      <strong v-if="card.price">{{ card.price }}</strong>
                      <button v-if="card.type === 'venue_product'" class="agent-book-button" type="button" @click="prepareAgentVenueBooking(card)">约球</button>
                      <button type="button" @click="handleAgentCardAction(card)">
                        <Users v-if="card.type === 'activity'" :size="15" />
                        {{ agentCardActionLabel(card) }}
                      </button>
                    </div>
                  </article>
                </div>
              </section>
            </template>
          </article>
          <div v-if="visibleAgentQuickReplies.length" class="agent-quick-list" :class="{ followup: hasAgentConversation }">
            <div class="agent-quick-title">
              <span>{{ hasAgentConversation ? '继续操作' : '试试这样问' }}</span>
            </div>
            <div class="agent-quick-stack">
              <button v-for="reply in visibleAgentQuickReplies" :key="reply" type="button" @click="handleAgentQuickReply(reply)">
                <span>{{ reply }}</span>
              </button>
            </div>
          </div>
          <section v-if="agentSportPickerVisible" id="agent-sport-picker" class="agent-sport-picker" aria-label="选择运动类型">
            <div class="agent-sport-picker-head">
              <div>
                <span>按哪些球类查找</span>
                <small>可多选</small>
              </div>
              <button type="button" aria-label="取消选择" @click="closeAgentSportPicker">
                <X :size="16" />
              </button>
            </div>
            <p>{{ agentPendingQuickReply }}</p>
            <div class="agent-sport-options">
              <button
                type="button"
                :class="{ active: !agentSelectedSportCodes.length }"
                :aria-pressed="!agentSelectedSportCodes.length"
                @click="toggleAgentSport('')"
              >不限</button>
              <button
                v-for="sport in sports"
                :key="sport.code"
                type="button"
                :class="{ active: agentSelectedSportCodes.includes(sport.code) }"
                :aria-pressed="agentSelectedSportCodes.includes(sport.code)"
                @click="toggleAgentSport(sport.code)"
              >{{ sport.name }}</button>
            </div>
            <button class="primary agent-sport-confirm" type="button" @click="confirmAgentQuickReply">
              按所选球类查询
            </button>
          </section>
          <article v-if="agentThinking" class="agent-message assistant agent-thinking">
            <div class="agent-bubble">
              <span>{{ agentProgressText || 'AI 正在思考' }}</span>
              <i></i>
              <i></i>
              <i></i>
            </div>
          </article>
          <div id="agent-chat-bottom"></div>
        </section>

        <div class="agent-input-bar">
          <input
            id="agent-input"
            v-model="agentInput"
            :placeholder="agentInputPlaceholder"
            @keyup.enter="sendAgentMessage()"
          />
          <button class="primary" type="button" @click="sendAgentMessage()" :disabled="loading || !agentInput.trim()">
            <Send :size="17" />
          </button>
        </div>

        <div v-if="agentVenueBooking" class="agent-booking-backdrop" @click="closeAgentVenueBooking"></div>
        <section v-if="agentVenueBooking" class="agent-booking-sheet" aria-label="确认购买并发起约球">
          <div class="agent-booking-head">
            <div>
              <span>确认约球</span>
              <strong>购买场地后发起活动</strong>
            </div>
            <button type="button" aria-label="关闭确认面板" @click="closeAgentVenueBooking"><X :size="17" /></button>
          </div>
          <div class="agent-booking-summary">
            <span>{{ agentBookingVenueName(agentVenueBooking) }}</span>
            <strong>{{ agentVenueBooking.product.title }}</strong>
            <em>{{ agentBookingInventory?.price ? yuan(agentBookingInventory.price) : agentVenueBooking.product.price }}</em>
          </div>
          <label class="form-field">
            <span>可预约时段</span>
            <select v-model.number="agentVenueBooking.inventoryId">
              <option v-for="inventory in agentVenueBooking.inventories" :key="inventory.id" :value="inventory.id">
                {{ agentInventoryText(inventory) }}
              </option>
            </select>
          </label>
          <div class="agent-booking-form-row">
            <label class="form-field">
              <span>计划人数（含自己）</span>
              <select v-model.number="agentVenueBooking.maxPlayers">
                <option v-for="count in PLAYER_COUNT_OPTIONS" :key="count" :value="count">{{ count }} 人</option>
              </select>
            </label>
            <label class="form-field">
              <span>水平要求</span>
              <select v-model="agentVenueBooking.levelRequired">
                <option>不限</option>
                <option>新手友好</option>
                <option>初级以上</option>
                <option>中级对抗</option>
              </select>
            </label>
          </div>
          <p class="agent-booking-tip">确认后将支付该时段团购，并在相同场所、日期和时间发起约球活动。</p>
          <div class="agent-booking-actions">
            <button type="button" @click="closeAgentVenueBooking">取消</button>
            <button class="primary" type="button" :disabled="agentVenueBookingSubmitting" @click="confirmAgentVenueBooking">
              {{ agentVenueBookingSubmitting ? '处理中' : '确认购买并发起' }}
            </button>
          </div>
        </section>

        <div v-if="agentHistoryVisible" class="agent-history-backdrop" @click="closeAgentHistory"></div>
        <section v-if="agentHistoryVisible" class="agent-history-sheet" aria-label="AI 助手聊天历史">
          <div class="agent-history-head">
            <div>
              <span>聊天记录</span>
              <strong>切换历史会话</strong>
            </div>
            <button type="button" @click="agentHistoryVisible = false" aria-label="关闭聊天记录">
              <X :size="17" />
            </button>
          </div>
          <div v-if="loadingAgentHistory" class="agent-history-empty">正在加载聊天记录</div>
          <div v-else-if="!agentConversations.length" class="agent-history-empty">暂无历史会话</div>
          <div v-else class="agent-history-list">
            <article
              v-for="conversation in agentConversations"
              :key="conversation.id"
              class="agent-history-item"
              :class="{ active: conversation.id === agentConversationId }"
            >
              <button class="agent-history-main" type="button" @click="switchAgentConversation(conversation)">
                <strong>{{ agentConversationTitle(conversation) }}</strong>
                <span>{{ agentConversationTime(conversation) }}</span>
              </button>
              <button class="agent-history-delete" type="button" @click="deleteAgentConversation(conversation)" aria-label="删除会话">
                <Trash2 :size="15" />
              </button>
            </article>
          </div>
          <div v-if="deletingAgentConversation" class="agent-history-confirm">
            <div>
              <strong>确认删除这条会话？</strong>
              <span>{{ agentConversationTitle(deletingAgentConversation) }}</span>
            </div>
            <div>
              <button type="button" @click="cancelDeleteAgentConversation">取消</button>
              <button class="danger" type="button" @click="confirmDeleteAgentConversation" :disabled="loadingAgentHistory">删除</button>
            </div>
          </div>
        </section>
      </section>

      <section v-else-if="activeTab === 'profile'" class="page-stack">
        <div class="section-title">
          <div>
            <span>{{ profileOrderPageVisible ? '我的' : profileSubtitle }}</span>
            <strong>{{ profileOrderPageVisible ? '订单与购物车' : profileTitle }}</strong>
          </div>
          <button v-if="profileMode === 'public' && !profileOrderPageVisible" class="ghost" @click="backFromPublicProfile"><ChevronLeft :size="16" /> 返回</button>
          <button v-else-if="profileOrderPageVisible" class="ghost" @click="closeProfileOrderPage"><ChevronLeft :size="16" /> 返回</button>
          <button v-else class="ghost" @click="refreshProfile">刷新</button>
        </div>

        <section v-if="profileMode === 'me' && !loggedIn" class="guest-card">
          <div class="guest-avatar"><UserRound :size="28" /></div>
          <div>
            <h3>登录后查看个人中心</h3>
            <p>订单、核销码、购物车都会同步到这里</p>
          </div>
          <button class="primary" @click="openAuthPage('profile')"><LogIn :size="17" /> 登录/注册</button>
        </section>

        <section v-else-if="!profileOrderPageVisible" class="profile-card">
          <div class="profile-main">
            <img
              :src="profileAvatar || FALLBACK_AVATAR"
              :alt="profileNickname || '用户头像'"
              @error="imageFallback($event, FALLBACK_AVATAR)"
            />
            <div>
              <h3>{{ profileNickname || '球友' }}</h3>
              <p>{{ profileMode === 'me' ? maskedPhone(userProfile?.phone) : '公开主页' }}</p>
              <div class="profile-tags">
                <span>{{ profileCity || locationLabel }}</span>
                <span>{{ profileLevel || '新手' }}</span>
              </div>
            </div>
            <div v-if="profileMode === 'me'" class="profile-actions">
              <button class="ghost" type="button" @click="openProfileEditor">编辑</button>
              <button class="ghost" type="button" @click="logout">退出</button>
            </div>
            <button
              v-else
              class="ghost"
              :class="{ active: viewedUserProfile?.followed }"
              @click="toggleProfileFollow"
            >
              {{ viewedUserProfile?.followed ? '已关注' : '关注' }}
            </button>
          </div>

          <div class="profile-detail">
            <span v-if="profileMode === 'me'">用户名：{{ userProfile?.username || '未设置' }}</span>
            <span v-if="profileMode === 'me'">邮箱：{{ userProfile?.email || '未绑定' }}</span>
            <span v-if="profileMode === 'me'">运动：{{ sportNameByCode(socialProfile?.sportCode || selectedSport || 'badminton') }}</span>
            <span v-if="profileMode === 'me'">打法：{{ socialProfile?.playStyle || '双打' }}</span>
            <span>偏好：{{ profilePreferTime || '工作日晚上 / 周末下午' }}</span>
            <span>加入：{{ formatDateTime(profileCreatedAt) }}</span>
          </div>

          <div v-if="profileMode === 'me'" class="profile-entry-grid" aria-label="订单入口">
            <button type="button" :class="{ active: profileView === 'orders' }" @click="openProfileView('orders')">
              <ClipboardList :size="27" />
              <span>全部订单</span>
            </button>
            <button type="button" :class="{ active: profileView === 'paid' }" @click="openProfileView('paid')">
              <CreditCard :size="27" />
              <span>已支付</span>
            </button>
            <button type="button" :class="{ active: profileView === 'cart' }" @click="openProfileView('cart')">
              <ShoppingCart :size="27" />
              <span>购物车</span>
            </button>
          </div>
        </section>

        <section v-if="profileMode === 'me' && loggedIn && !profileOrderPageVisible && profileEditorVisible" class="profile-editor-panel">
          <div class="sheet-title">
            <div>
              <span>球友资料</span>
              <strong>编辑约球名片</strong>
            </div>
            <button class="ghost icon-only" type="button" @click="closeProfileEditor" aria-label="关闭"><X :size="18" /></button>
          </div>
          <div class="profile-form-grid">
            <div class="avatar-edit wide">
              <img :src="profileForm.avatar || profileAvatar || FALLBACK_AVATAR" alt="头像预览" @error="imageFallback($event, FALLBACK_AVATAR)" />
              <div>
                <strong>{{ profileForm.nickname || profileNickname || '球友' }}</strong>
                <label class="avatar-upload">
                  <Camera :size="15" />
                  更换头像
                  <input type="file" accept="image/jpeg,image/png,image/webp" @change="uploadAvatar" />
                </label>
              </div>
            </div>
            <label>
              <span>昵称</span>
              <input v-model="profileForm.nickname" placeholder="陈予" />
            </label>
            <label>
              <span>用户名</span>
              <input v-model="profileForm.username" placeholder="chenyu" />
            </label>
            <label>
              <span>手机号</span>
              <input v-model="profileForm.phone" inputmode="tel" maxlength="11" placeholder="可选，用于账号登录" />
            </label>
            <label class="wide">
              <span>邮箱</span>
              <input v-model="profileForm.email" type="email" placeholder="chen@example.com" />
            </label>
            <label class="wide">
              <span>新密码</span>
              <input v-model="profileForm.password" type="password" placeholder="留空则不修改，至少 6 位" />
            </label>
            <label>
              <span>常打运动</span>
              <select v-model="profileForm.sportCode">
                <option v-for="sport in sports" :key="sport.code" :value="sport.code">{{ sport.name }}</option>
              </select>
            </label>
            <label>
              <span>水平</span>
              <select v-model="profileForm.level">
                <option>新手</option>
                <option>初级</option>
                <option>中级</option>
                <option>高级</option>
              </select>
            </label>
            <label>
              <span>城市</span>
              <input v-model="profileForm.city" placeholder="西安" />
            </label>
            <label>
              <span>区域</span>
              <input v-model="profileForm.area" placeholder="未央区" />
            </label>
            <label>
              <span>打法/位置</span>
              <input v-model="profileForm.playStyle" placeholder="双打、单打、守门员..." />
            </label>
            <label>
              <span>可约时间</span>
              <input v-model="profileForm.availableTime" placeholder="工作日晚上 / 周末下午" />
            </label>
            <label class="wide">
              <span>个人介绍</span>
              <textarea v-model="profileForm.intro" maxlength="120" placeholder="写一句方便别人判断是否合拍的话" />
            </label>
            <label class="toggle-row wide">
              <span>允许球友邀请我参加活动</span>
              <input v-model="profileForm.allowInvite" type="checkbox" />
            </label>
          </div>
          <div class="sheet-actions">
            <button type="button" @click="closeProfileEditor">取消</button>
            <button class="primary" type="button" @click="saveSocialProfile">保存资料</button>
          </div>
        </section>

        <section v-if="!profileOrderPageVisible && (profileMode === 'public' || loggedIn)" class="profile-blogs">
          <div class="section-title compact-title">
            <div>
              <span>{{ profileMode === 'public' ? 'TA 的' : '我的' }}</span>
              <strong>动态</strong>
            </div>
            <button v-if="profileMode === 'me'" class="ghost" type="button" @click="openBlogPublisher">
              <PenLine :size="15" />
              发动态
            </button>
          </div>
          <div v-if="!profileBlogs.length" class="empty-box">暂无动态</div>
          <div v-else class="blog-masonry">
            <article v-for="blog in profileBlogs" :key="blog.id" class="blog-card clickable-card" @click="openBlogDetail(blog)">
              <button class="blog-cover-button" type="button" @click.stop="openBlogDetail(blog)">
                <img
                  :src="blog.images[0] || blog.relatedCoverUrl || FALLBACK_IMAGE"
                  :alt="blog.title"
                  loading="lazy"
                  decoding="async"
                  @error="imageFallback"
                />
              </button>
              <div class="blog-body">
                <h3>{{ blog.title }}</h3>
                <p>{{ blog.content }}</p>
                <button class="blog-related" type="button" @click.stop="openBlogRelated(blog)">
                  <ShoppingBag :size="14" />
                  <span>{{ blog.relatedTitle }}</span>
                  <strong v-if="blog.relatedPrice">{{ yuan(blog.relatedPrice) }}</strong>
                </button>
                <div class="blog-author-row">
                  <button class="blog-author-link" type="button" @click.stop="openUserProfile(blog.userId)">
                    <img :src="blog.avatar || FALLBACK_AVATAR" :alt="blog.nickname" loading="lazy" decoding="async" @error="imageFallback($event, FALLBACK_AVATAR)" />
                    <span>{{ blog.nickname }}</span>
                  </button>
                  <button
                    v-if="profileMode === 'public'"
                    type="button"
                    class="follow-mini"
                    :class="{ active: blog.followed }"
                    @click.stop="toggleBlogFollow(blog)"
                  >
                    {{ blog.followed ? '已关注' : '关注' }}
                  </button>
                  <button v-else type="button" class="delete-mini icon-only-mini" aria-label="删除动态" @click.stop="deleteBlog(blog)">
                    <Trash2 :size="14" />
                  </button>
                  <button type="button" class="like-mini" :class="{ active: blog.isLiked }" @click.stop="toggleBlogLike(blog)">
                    <Heart :size="15" />
                    {{ blog.liked }}
                  </button>
                </div>
              </div>
            </article>
          </div>
          <div v-if="loadingMore" class="load-more-state">正在加载更多动态</div>
          <div v-else-if="profileBlogs.length && !hasMoreProfileBlogs" class="load-more-state muted-state">已经到底了</div>
        </section>

        <section v-if="profileMode === 'me' && loggedIn && profileOrderPageVisible" class="profile-order-tabs">
          <div class="profile-segment" aria-label="订单页切换">
            <button type="button" :class="{ active: profileView === 'orders' }" @click="openProfileView('orders')">
              <span>全部订单</span>
            </button>
            <button type="button" :class="{ active: profileView === 'paid' }" @click="openProfileView('paid')">
              <span>已支付</span>
            </button>
            <button type="button" :class="{ active: profileView === 'cart' }" @click="openProfileView('cart')">
              <span>购物车</span>
            </button>
          </div>
        </section>

        <section v-if="profileMode === 'me' && loggedIn && profileOrderPageVisible && profileView === 'cart'" id="profile-cart" class="profile-orders">
          <div class="section-title compact-title">
            <div>
              <span>我的</span>
              <strong>购物车</strong>
            </div>
          </div>
          <div v-if="!cartItems.length" class="empty-box">购物车暂无商品</div>
          <article v-for="item in cartItems" :key="item.id" class="cart-item-row">
            <img :src="item.coverUrl || FALLBACK_IMAGE" :alt="item.productName" loading="lazy" decoding="async" @error="imageFallback" />
            <div>
              <h3>{{ item.productName }}</h3>
              <p>{{ item.brand }}</p>
              <small>{{ item.meta }} · {{ yuan(item.price) }}</small>
              <div class="cart-qty" aria-label="调整数量">
                <button type="button" @click="updateCartQuantity(item, item.quantity - 1)" :disabled="item.quantity <= 1">-</button>
                <span>{{ item.quantity }}</span>
                <button type="button" @click="updateCartQuantity(item, item.quantity + 1)">+</button>
              </div>
            </div>
            <div class="order-side">
              <strong>{{ yuan(item.amount) }}</strong>
              <span>已加购</span>
              <button type="button" class="ghost mini-delete" @click="removeCartItem(item)">删除</button>
            </div>
          </article>
          <div v-if="loggedIn && cartItems.length" class="cart-summary">
            <span>共 {{ cartCount }} 件</span>
            <strong>{{ yuan(cartAmount) }}</strong>
            <button @click="checkoutCart">结算并支付</button>
          </div>
        </section>

        <section v-if="profileMode === 'me' && loggedIn && profileOrderPageVisible && (profileView === 'orders' || profileView === 'paid')" class="profile-orders">
          <div class="section-title compact-title">
            <div>
              <span>我的</span>
              <strong>场所订单</strong>
            </div>
          </div>
          <div v-if="!profileVenueOrderCards.length" class="empty-box">暂无场所订单</div>
          <article v-for="order in profileVenueOrderCards" :key="order.key" class="order-card">
            <div>
              <h3>{{ order.title }}</h3>
              <p>{{ order.subtitle }}</p>
              <small>{{ order.meta }}</small>
            </div>
            <div class="order-side">
              <strong>{{ yuan(order.amount) }}</strong>
              <span>{{ order.status }}</span>
              <em>{{ order.code }}</em>
            </div>
          </article>
        </section>

        <section v-if="profileMode === 'me' && loggedIn && profileOrderPageVisible && (profileView === 'orders' || profileView === 'paid')" class="profile-orders">
          <div class="section-title compact-title">
            <div>
              <span>我的</span>
              <strong>装备订单</strong>
            </div>
          </div>
          <div v-if="!profileEquipmentOrderCards.length" class="empty-box">暂无装备订单</div>
          <article v-for="order in profileEquipmentOrderCards" :key="order.key" class="order-card">
            <div>
              <h3>{{ order.title }}</h3>
              <p>{{ order.subtitle }}</p>
              <small>{{ order.meta }}</small>
            </div>
            <div class="order-side">
              <strong>{{ yuan(order.amount) }}</strong>
              <span>{{ order.status }}</span>
              <em>{{ order.code }}</em>
            </div>
          </article>
        </section>
      </section>
    </main>

    <button
      v-if="showAgentFab"
      class="agent-fab"
      type="button"
      @click="openAssistant(assistantFabPrompt)"
    >
      <Bot :size="18" />
      <span>AI 助手</span>
    </button>

    <nav class="bottom-nav">
      <button
        v-for="tab in bottomTabs"
        :key="tab.key"
        :class="{ active: activeTab === tab.key && !(tab.key === 'profile' && profileMode === 'public') }"
        @click="switchTab(tab.key)"
      >
        <component :is="tab.icon" :size="19" />
        <span>{{ tab.label }}</span>
      </button>
    </nav>
    </template>
  </div>
</template>

