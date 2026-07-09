<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Activity,
  Camera,
  ChevronLeft,
  ClipboardList,
  CreditCard,
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
import { api, clearToken, getToken, PageResult, setToken } from './api/client'
import { locateWithAmapFirst } from './api/amapGeolocation'

type Tab = 'home' | 'seckill' | 'social' | 'equipment' | 'profile'
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

type BlogChannel = 'follow' | 'recommend' | 'sport'
type ProfileMode = 'me' | 'public'

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
const PLACE_RADIUS_STEPS = [DEFAULT_PLACE_RADIUS, 10000, 20000, 30000]
const MIN_PLACE_RESULTS = 6
const PLACE_PAGE_SIZE = 20
const PRODUCT_PAGE_SIZE = 12
const SOCIAL_PAGE_SIZE = 12
const BLOG_PAGE_SIZE = 10
const VENUE_TEMPLATE_COUNT = 8
const FALLBACK_IMAGE = '/api/files/31/download'
const FALLBACK_AVATAR = '/api/files/1/download'
const SALE_RANKS_BY_SPORT: Record<string, number[]> = {
  badminton: [1, 2, 3, 4, 7],
  football: [5],
  basketball: [6, 8]
}

const activeTab = ref<Tab>('home')
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
const venueOrders = ref<ProfileOrderCard[]>([])
const equipmentOrders = ref<ProfileOrderCard[]>([])
const cartItems = ref<CartItem[]>([])
const userProfile = ref<UserProfile | null>(null)
const socialProfile = ref<Player | null>(null)
const viewedUserProfile = ref<UserPublicProfile | null>(null)
const profileBlogs = ref<BlogPost[]>([])
const selectedBlog = ref<BlogPost | null>(null)
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
const profileBlogsPage = ref(1)
const profileBlogsTotal = ref(0)

const loggedIn = computed(() => Boolean(authToken.value))
const activeSport = computed(() => sports.value.find((sport) => sport.code === selectedSport.value) || ALL_SPORT)
const cartAmount = computed(() => cartItems.value.reduce((sum, item) => sum + Number(item.amount), 0))
const showPhoneHeader = computed(() => !blogComposerVisible.value && !(activeTab.value === 'home' && selectedPlace.value))
const showDiscoveryHeader = computed(() => !blogComposerVisible.value && activeTab.value !== 'profile')
const showCategoryHeader = computed(() => activeTab.value === 'equipment' && Boolean(selectedSport.value))
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
function nextPlaceRadius() {
  return PLACE_RADIUS_STEPS.find((radius) => radius > placeQuery.radius)
}

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
  { key: 'profile' as const, label: '我的', icon: UserRound }
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
  levelRequired: '中级',
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
}

function handleRequestError(error: unknown) {
  const text = errorMessage(error)
  if (text.includes('请先登录')) {
    resetLoginState()
  }
  return text
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
    const result = await api<LoginCodeResponse>('/api/auth/code', {
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
    const result = await api<{ token: string }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(body)
    })
    setToken(result.token)
    authToken.value = result.token
    activeTab.value = authReturnTab.value
    authPageVisible.value = false
    const located = await refreshCurrentLocation({ reload: false, showMessage: false })
    await loadCurrentTab()
    message.value = located ? '登录成功，已刷新当前位置' : '登录成功，未获取当前位置'
  })
}

async function loadSports() {
  sports.value = await api<SportType[]>('/api/sports')
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
  const sportCode = place.sportCode || selectedSport.value
  const sportRanks = SALE_RANKS_BY_SPORT[sportCode]
  if (sportRanks?.length) {
    return sportRanks[index % sportRanks.length]
  }
  return (index % VENUE_TEMPLATE_COUNT) + 1
}

function reviewRankFor(place: Place) {
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

function setPagingParams(params: URLSearchParams, page: number, pageSize: number, defaultPageSize: number) {
  if (page > 1) params.set('page', String(page))
  if (pageSize !== defaultPageSize) params.set('size', String(pageSize))
}

async function loadPlaces(page = 1, append = false, autoExpand = true) {
  const params = new URLSearchParams()
  if (selectedSport.value) params.set('sport', selectedSport.value)
  params.set('lng', String(placeQuery.lng))
  params.set('lat', String(placeQuery.lat))
  if (placeQuery.radius !== DEFAULT_PLACE_RADIUS) params.set('radius', String(placeQuery.radius))
  setPagingParams(params, page, PLACE_PAGE_SIZE, 20)
  setTrimmedParam(params, 'keyword', placeQuery.keyword)
  const result = await api<PageResult<Place>>(`/api/places/nearby?${params}`)
  places.value = append ? appendById(places.value, result.records) : result.records
  placesPage.value = result.page
  placesTotal.value = result.total
  if (!append && selectedPlace.value && !result.records.some((place) => place.id === selectedPlace.value?.id)) {
    selectedPlace.value = null
    venueReviews.value = []
  }
  if (autoExpand && page === 1 && !placeQuery.keyword.trim() && places.value.length < MIN_PLACE_RESULTS) {
    const radius = nextPlaceRadius()
    if (radius) {
      placeQuery.radius = radius
      await loadPlaces(1, true, true)
      if (!append && places.value.length > result.records.length) {
        message.value = `附近结果较少，已扩大到 ${Math.round(placeQuery.radius / 1000)}km`
      }
    }
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
    const productsForPlace = await api<VenueItem[]>(`/api/items/1?${params}`)
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
  const result = await api<PageResult<VenueItem>>(`/api/items/1?${params}`)
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
  const result = await api<PageResult<EquipmentItem>>(`/api/items/2?${params}`)
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
  categories.value = await api<Category[]>(`/api/categories/2?${new URLSearchParams({ sport: selectedSport.value })}`)
  productCategorySport.value = selectedSport.value
}

async function loadSeckill() {
  const params = new URLSearchParams()
  if (selectedSport.value) params.set('sport', selectedSport.value)
  if (productQuery.categoryId) params.set('categoryId', productQuery.categoryId)
  seckillActivities.value = await api<SeckillActivity[]>(`/api/seckill/2?${params}`)
}

async function loadBlogs(page = 1, append = false) {
  await ensureUserProfile()
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
    const result = await api<ScrollResult<BlogPost>>(`/api/blogs/of/follow?${params}`)
    const records = result.list || []
    blogs.value = append ? appendById(blogs.value, records) : records
    blogsPage.value = page
    blogsTotal.value = blogs.value.length
    followFeedLastId.value = result.minTime || undefined
    followFeedOffset.value = result.offset || 0
    followFeedHasMore.value = Boolean(result.minTime) && records.length >= BLOG_PAGE_SIZE
    return
  }
  const params = new URLSearchParams({
    channel: 'recommend'
  })
  if (blogChannel.value === 'sport' && blogSport.value) params.set('sport', blogSport.value)
  setTrimmedParam(params, 'keyword', blogKeyword.value)
  setPagingParams(params, page, BLOG_PAGE_SIZE, 10)
  const result = await api<PageResult<BlogPost>>(`/api/blogs?${params}`)
  blogs.value = append ? appendById(blogs.value, result.records) : result.records
  blogsPage.value = result.page
  blogsTotal.value = result.total
}

async function loadSeckillCategories() {
  if (!selectedSport.value) {
    seckillCategories.value = []
    seckillCategorySport.value = selectedSport.value
    return
  }
  const categoryParams = new URLSearchParams({ sport: selectedSport.value })
  seckillCategories.value = await api<Category[]>(`/api/categories/2?${categoryParams}`)
  seckillCategorySport.value = selectedSport.value
}

async function loadSocial(page = 1, append = false) {
  const params = new URLSearchParams()
  if (selectedSport.value) params.set('sport', selectedSport.value)
  const activityParams = new URLSearchParams()
  if (selectedSport.value) activityParams.set('sport', selectedSport.value)
  if (!loggedIn.value) {
    params.set('lng', String(placeQuery.lng))
    params.set('lat', String(placeQuery.lat))
  }
  if (!loggedIn.value && placeQuery.city.trim()) {
    params.set('city', placeQuery.city.trim())
    activityParams.set('city', placeQuery.city.trim())
  }
  setPagingParams(params, page, SOCIAL_PAGE_SIZE, 12)
  setPagingParams(activityParams, page, SOCIAL_PAGE_SIZE, 12)
  const playersResult = await api<PageResult<Player>>(`/api/social/players?${params}`)
  const activitiesResult = await api<PageResult<SportActivity>>(`/api/social/activities?${activityParams}`)
  players.value = append ? appendByKey(players.value, playersResult.records, (player) => player.userId) : playersResult.records
  activities.value = append ? appendById(activities.value, activitiesResult.records) : activitiesResult.records
  playersPage.value = playersResult.page
  playersTotal.value = playersResult.total
  activitiesPage.value = activitiesResult.page
  activitiesTotal.value = activitiesResult.total
}

async function loadCartItems(force = false) {
  if (!loggedIn.value) {
    cartItems.value = []
    cartCount.value = 0
    cartLoaded.value = false
    return
  }
  if (cartLoaded.value && !force) return
  cartItems.value = await api<CartItem[]>('/api/cart')
  cartCount.value = cartItems.value.reduce((sum, item) => sum + item.quantity, 0)
  cartLoaded.value = true
}

async function loadVenueOrders() {
  if (!loggedIn.value) {
    venueOrders.value = []
    return
  }
  venueOrders.value = await api<ProfileOrderCard[]>('/api/orders/1')
}

async function loadEquipmentOrders() {
  if (!loggedIn.value) {
    equipmentOrders.value = []
    return
  }
  equipmentOrders.value = await api<ProfileOrderCard[]>('/api/orders/2')
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
  userProfile.value = await api<UserProfile>('/api/auth/me')
}

async function loadSocialProfile() {
  if (!loggedIn.value) {
    socialProfile.value = null
    return
  }
  try {
    socialProfile.value = await api<Player>('/api/social/profile/me')
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
  viewedUserProfile.value = await api<UserPublicProfile>(`/api/auth/users/${userId}`)
}

async function loadProfileBlogs(userId: number, page = 1, append = false) {
  const params = new URLSearchParams()
  setPagingParams(params, page, BLOG_PAGE_SIZE, 10)
  const result = await api<PageResult<BlogPost>>(`/api/blogs/of/user/${userId}?${params}`)
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
  const failed = (await Promise.allSettled(tasks)).find((result) => result.status === 'rejected')
  if (failed && failed.status === 'rejected') {
    message.value = handleRequestError(failed.reason)
  }
}

function nearPageBottom() {
  const scrollTop = window.scrollY || document.documentElement.scrollTop
  const viewport = window.innerHeight || document.documentElement.clientHeight
  const height = document.documentElement.scrollHeight
  return scrollTop + viewport >= height - 180
}

async function loadMoreCurrentTab() {
  if (loading.value || loadingMore.value) return
  if (activeTab.value === 'home' && selectedPlace.value) return
  if (activeTab.value === 'home' && venueSaleView.value && !hasMoreVenueSales.value) return
  if (activeTab.value === 'home' && !venueSaleView.value && !hasMorePlaces.value && !nextPlaceRadius()) return
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
        if (!hasMorePlaces.value) {
          const radius = nextPlaceRadius()
          if (radius) {
            placeQuery.radius = radius
            await loadPlaces(1, true)
            message.value = `已扩大到 ${Math.round(radius / 1000)}km 继续查找`
          }
        } else {
          await loadPlaces(placesPage.value + 1, true)
        }
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
  if (nearPageBottom()) {
    void loadMoreCurrentTab()
  }
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
  selectedBlog.value = null
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
    await api(`/api/blogs/${blog.id}/like`, { method: 'PUT' })
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
    await api(`/api/blogs/${blog.id}`, { method: 'DELETE' })
    removeBlogFromState(blog.id)
  }, '动态已删除')
}

async function toggleBlogFollow(blog: BlogPost) {
  if (!requireLogin('请先登录后关注作者')) return
  const next = !blog.followed
  await wrap(async () => {
    await api(`/api/follows/${blog.userId}/${next}`, { method: 'PUT' })
    syncBlogFollowState(blog.userId, next)
  }, next ? '已关注作者' : '已取消关注')
}

async function openBlogDetail(blog: BlogPost) {
  await wrap(async () => {
    await ensureUserProfile()
    selectedBlog.value = await api<BlogPost>(`/api/blogs/${blog.id}`)
  })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function closeBlogDetail() {
  selectedBlog.value = null
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
    await api<{ blogId: number }>('/api/blogs', {
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
    await api(`/api/follows/${userId}/${next}`, { method: 'PUT' })
    if (viewedUserProfile.value) {
      syncBlogFollowState(userId, next)
      viewedUserProfile.value = { ...viewedUserProfile.value, followed: next }
    }
  }, next ? '已关注球友' : '已取消关注')
}

async function openBlogRelated(blog: BlogPost) {
  selectedBlog.value = null
  if (blog.relatedType === 'EQUIPMENT') {
    activeTab.value = 'equipment'
    productQuery.keyword = ''
    productQuery.categoryId = ''
    productsPage.value = 1
    productsTotal.value = 0
    await wrap(async () => {
      const [detail] = await Promise.all([
        api<EquipmentItem>(`/api/items/2/${blog.relatedId}`),
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
      api<VenueItem>(`/api/items/1/${blog.relatedId}`),
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
  return api<FileMetadata>('/api/files/upload', {
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
    await api('/api/auth/me', {
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
    await api('/api/social/profile/me', {
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
    const located = await locateWithAmapFirst()
    const accuracy = Math.round(located.accuracy)
    locationAccuracy.value = accuracy
    placeQuery.lng = located.lng
    placeQuery.lat = located.lat
    placeQuery.radius = DEFAULT_PLACE_RADIUS
    try {
      const location = await api<RegeoLocation>(`/api/places/regeo?${new URLSearchParams({
        lng: String(placeQuery.lng),
        lat: String(placeQuery.lat)
      })}`)
      placeQuery.city = location.city || ''
      placeQuery.preciseAddress = location.shortAddress || location.formattedAddress || ''
    } catch {
      placeQuery.city = ''
      placeQuery.preciseAddress = ''
    }
    if (loggedIn.value) {
      await api('/api/auth/location', {
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
      const sourceLabel = located.source === 'amap' ? '高德高精度定位' : '浏览器定位'
      message.value = accuracy > 1000
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
  document.getElementById('venue-sale-page')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function closeVenueSalePage() {
  venueSaleView.value = null
  venueSaleItems.value = []
  venueSalesPage.value = 1
  venueSalesTotal.value = 0
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

async function showSocial() {
  refreshActivityWindowIfExpired()
  await switchTab('social')
  await wrap(ensureActivityPlaceOptions)
  await nextTick()
  document.getElementById('activity-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
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

function closePlaceDetail() {
  selectedPlace.value = null
  venueReviews.value = []
  purchaseNotice.value = ''
}

async function switchPlaceDetailTab(tab: 'deals' | 'reviews') {
  placeDetailTab.value = tab
  if (tab === 'reviews' && selectedPlace.value && !venueReviews.value.length) {
    await wrap(() => loadVenueReviewsForPlace(selectedPlace.value as Place))
  }
}

async function loadVenueReviewsForPlace(place: Place) {
  venueReviews.value = await api<VenueReview[]>(`/api/venues/${reviewRankFor(place)}/reviews?size=5`)
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
    const created = await api<{ orderId: number; verifyCode: string; order: VenueOrder }>('/api/payments', {
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
    await api('/api/cart', {
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
    await api('/api/cart', {
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
    await api('/api/payments', {
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
    await api<{ orderId: number }>('/api/payments/cart', {
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
    await api(`/api/cart/${item.type}/${item.id}`, { method: 'DELETE' })
    cartItems.value = cartItems.value.filter((record) => !(record.type === item.type && record.id === item.id))
    cartCount.value = cartItems.value.reduce((sum, record) => sum + record.quantity, 0)
    cartLoaded.value = true
  }, '已移出购物车')
}

async function updateCartQuantity(item: CartItem, quantity: number) {
  const nextQuantity = Math.min(Math.max(quantity, 1), 99)
  if (nextQuantity === item.quantity) return
  await wrap(async () => {
    await api(`/api/cart/${item.type}/${item.id}`, {
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
    const result = await api<{ orderId: number }>(`/api/seckill/2/${activityId}`, { method: 'POST' })
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
  await wrap(async () => {
    await api('/api/social/activities', {
      method: 'POST',
      body: JSON.stringify({
        ...activityForm,
        sportCode: activityForm.sportCode,
        city: activityForm.city || locationLabel.value,
        venueId: null,
        startTime: `${activityForm.startTime}:00`,
        endTime: `${activityForm.endTime}:00`
      })
    })
    await loadSocial()
  }, '约球活动已发布')
}

async function joinActivity(id: number) {
  if (!requireLogin('请先登录后加入活动')) return
  await wrap(async () => {
    await api(`/api/social/activities/${id}/join`, { method: 'POST' })
    await loadSocial()
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
  const inventories = await api<VenueInventory[]>(`/api/items/1/${product.id}/inventories`)
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
  window.addEventListener('scroll', handleWindowScroll, { passive: true })
  refreshActivityWindowIfExpired(true)
  try {
    await loadSports()
  } catch {
    sports.value = fallbackSports
  }
  await useCurrentLocation()
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
            <div class="auth-input">
              <KeyRound :size="18" />
              <input v-model="passwordLoginForm.password" type="password" placeholder="请输入密码" />
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
    <header v-if="showPhoneHeader" class="phone-header" :class="{ compact: !showDiscoveryHeader }">
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

      <div v-if="showDiscoveryHeader" class="search-bar">
        <Search :size="18" />
        <input v-model="headerSearchKeyword" :placeholder="headerSearchPlaceholder" @keyup.enter="searchPlaces" />
        <button @click="searchPlaces">搜索</button>
      </div>

      <div v-if="showDiscoveryHeader && activeTab !== 'seckill'" class="sport-scroll">
        <button :class="{ active: !selectedSport }" @click="changeSport('')">全部</button>
        <button
          v-for="sport in sports"
          :key="sport.code"
          :class="{ active: selectedSport === sport.code }"
          @click="changeSport(sport.code)"
        >
          {{ sport.name }}
        </button>
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

    <div v-if="message" class="toast" role="status">
      <span>{{ message }}</span>
      <button class="toast-close" type="button" aria-label="关闭提示" @click="closeMessage">
        <X :size="14" />
      </button>
    </div>

    <main class="phone-main">
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
            <input v-model="blogPublishForm.title" maxlength="48" placeholder="比如：黄金单场适合下班后一小时强度局" />
          </label>

          <label class="form-field">
            <span>正文</span>
            <textarea v-model="blogPublishForm.content" maxlength="500" placeholder="写写体验、适合人群、场地或装备感受" />
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
                  {{ buyingVenueItemId === item.id ? '购买中' : '抢购' }}
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
              <img class="review-avatar" :src="review.avatar" :alt="review.nickname" @error="imageFallback($event, FALLBACK_AVATAR)" />
              <div>
                <div class="review-head">
                  <h3>{{ review.nickname }}</h3>
                  <small>{{ reviewDate(review.createdAt) }}</small>
                </div>
                <p class="review-stars">{{ '★★★★★'.slice(0, Math.round(review.rating || 5)) }} 超赞</p>
                <p>{{ review.content }}</p>
                <div v-if="reviewImages(review).length" class="review-images">
                  <img v-for="image in reviewImages(review).slice(0, 3)" :key="image" :src="image" :alt="review.nickname" @error="imageFallback" />
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
            <img :src="place.coverUrl || FALLBACK_IMAGE" :alt="place.name" @error="imageFallback" />
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
            {{ nextPlaceRadius() ? '继续上滑，将扩大附近范围' : '已经到底了' }}
          </div>
        </section>
        </template>

        <section v-else id="venue-sale-page" class="venue-sale-page">
          <div class="section-title">
            <div>
              <span>{{ activeSport.name }} · {{ venueSaleView.subtitle }}</span>
              <strong>{{ venueSaleView.title }}</strong>
            </div>
            <button class="ghost" @click="closeVenueSalePage"><ChevronLeft :size="16" /> 返回</button>
          </div>

          <div v-if="!venueSaleItems.length" class="empty-box">暂无可购买项目</div>
          <article
            v-for="item in venueSaleItems"
            :id="itemDomId('VENUE_PRODUCT', item.id)"
            :key="item.id"
            class="venue-sale-row"
            :class="{ highlighted: isHighlighted('VENUE_PRODUCT', item.id) }"
          >
            <img :src="item.coverUrl || FALLBACK_IMAGE" :alt="item.title" @error="imageFallback" />
            <div>
              <div class="sale-row-head">
                <span class="service-type" :class="typeClass(item.productType)">{{ item.productTypeName }}</span>
                <small>{{ item.purchasable ? '可购买' : '已售罄' }}</small>
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
                <div>
                  <strong>{{ yuan(item.price) }}</strong>
                  <span v-if="hasDiscountPrice(item)">{{ yuan(item.originalPrice || 0) }}</span>
                </div>
                <div class="dual-action compact-actions">
                  <button type="button" @click="addVenueCart(item)" :disabled="!item.purchasable">加购</button>
                  <button class="primary" @click="buyVenueItem(item)" :disabled="buyingVenueItemId === item.id || !item.purchasable">
                    {{ buyingVenueItemId === item.id ? '购买中' : '抢购' }}
                  </button>
                </div>
              </div>
            </div>
          </article>
          <div v-if="loadingMore" class="load-more-state">正在加载更多可订项目</div>
          <div v-else-if="venueSaleItems.length && !hasMoreVenueSales" class="load-more-state muted-state">已经到底了</div>
        </section>
      </section>

      <section v-else-if="activeTab === 'seckill'" class="page-stack blog-page">
        <div class="blog-channel-row">
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
        </div>

        <div class="section-title">
          <div>
            <span>{{ blogChannel === 'follow' ? '关注动态' : blogChannel === 'sport' ? sportNameByCode(blogSport) : '推荐内容' }}</span>
            <strong>球友社区</strong>
          </div>
          <div class="title-actions">
            <button class="primary" type="button" @click="openBlogPublisher"><PenLine :size="16" /> 发动态</button>
            <button class="ghost" @click="() => loadBlogs()"><Heart :size="16" /> 刷新</button>
          </div>
        </div>

        <div v-if="!blogs.length" class="empty-box">暂无社区动态</div>
        <div v-else class="blog-masonry">
          <article v-for="blog in blogs" :key="blog.id" class="blog-card clickable-card" @click="openBlogDetail(blog)">
            <button class="blog-cover-button" type="button" @click.stop="openBlogDetail(blog)">
              <img
                :src="blog.images[0] || blog.relatedCoverUrl || FALLBACK_IMAGE"
                :alt="blog.title"
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
                  <img :src="blog.avatar || FALLBACK_AVATAR" :alt="blog.nickname" @error="imageFallback($event, FALLBACK_AVATAR)" />
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
          <button class="primary" @click="createActivity">发起约球</button>
        </div>
        <article v-for="item in activities" :key="item.id" class="activity-row">
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.venueName }} · {{ item.currentPlayers }}/{{ item.maxPlayers }} 人 · {{ item.feeType }}</p>
            <small>{{ item.startTime.replace('T', ' ') }}</small>
          </div>
          <button @click="joinActivity(item.id)">加入</button>
        </article>
        <article v-for="player in players" :key="player.userId" class="player-row clickable-card" @click="openUserProfile(player.userId)">
          <img :src="player.avatar || FALLBACK_AVATAR" :alt="player.nickname" @error="imageFallback($event, FALLBACK_AVATAR)" />
          <div>
            <h3>{{ player.nickname }} <span>{{ player.level }}</span></h3>
            <p>{{ player.area }} · {{ player.playStyle }} · {{ player.availableTime }}</p>
          </div>
        </article>
        <div v-if="loadingMore" class="load-more-state">正在加载更多约球内容</div>
        <div v-else-if="(players.length || activities.length) && !hasMoreSocial" class="load-more-state muted-state">已经到底了</div>
      </section>

      <section v-else-if="activeTab === 'equipment'" class="page-stack">
        <div class="section-title">
          <div>
            <span>{{ activeSport.name }}</span>
            <strong>装备商城</strong>
          </div>
          <button class="cart-chip cart-button" @click="showProfileCart" aria-label="查看购物车">
            <ShoppingCart :size="15" />
            <span v-if="cartCount">{{ cartCount }}</span>
          </button>
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
            <img :src="activity.coverUrl || FALLBACK_IMAGE" :alt="activity.productName" @error="imageFallback" />
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
          <img :src="product.coverUrl || FALLBACK_IMAGE" :alt="product.name" @error="imageFallback" />
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
                    <img :src="blog.avatar || FALLBACK_AVATAR" :alt="blog.nickname" @error="imageFallback($event, FALLBACK_AVATAR)" />
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
            <img :src="item.coverUrl || FALLBACK_IMAGE" :alt="item.productName" @error="imageFallback" />
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

