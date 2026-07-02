<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Activity,
  ChevronLeft,
  ClipboardList,
  CreditCard,
  ExternalLink,
  Heart,
  LocateFixed,
  LogIn,
  MapPin,
  MessageCircle,
  Phone,
  Search,
  ShieldCheck,
  ShoppingBag,
  ShoppingCart,
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

type EquipmentOrderItem = {
  productId: number
  productName: string
  coverUrl: string
  price: number
  quantity: number
}

type EquipmentOrder = {
  id: number
  userId: number
  totalAmount: number
  status: string
  address: string
  createdAt: string
  items: EquipmentOrderItem[]
}

type SeckillOrder = {
  id: number
  type: number
  activityId: number
  productId: number
  productName: string
  userId: number
  amount: number
  status: string
  createdAt: string
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
  phone: string
  email?: string
  username?: string
  nickname: string
  avatar?: string
  city: string
  level: string
  prefer_time?: string
  created_at?: string
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
  phone: string
  code?: string
  expireSeconds: number
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
const PLACE_PAGE_SIZE = 20
const PRODUCT_PAGE_SIZE = 12
const SOCIAL_PAGE_SIZE = 12
const BLOG_PAGE_SIZE = 10
const VENUE_TEMPLATE_COUNT = 8
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
const venueOrders = ref<VenueOrder[]>([])
const equipmentOrders = ref<EquipmentOrder[]>([])
const seckillOrders = ref<SeckillOrder[]>([])
const cartItems = ref<CartItem[]>([])
const userProfile = ref<UserProfile | null>(null)
const viewedUserProfile = ref<UserPublicProfile | null>(null)
const profileBlogs = ref<BlogPost[]>([])
const selectedBlog = ref<BlogPost | null>(null)
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
const profileBlogsPage = ref(1)
const profileBlogsTotal = ref(0)

const loggedIn = computed(() => Boolean(authToken.value))
const activeSport = computed(() => sports.value.find((sport) => sport.code === selectedSport.value) || ALL_SPORT)
const cartAmount = computed(() => cartItems.value.reduce((sum, item) => sum + Number(item.amount), 0))
const showPhoneHeader = computed(() => !(activeTab.value === 'home' && selectedPlace.value))
const showDiscoveryHeader = computed(() => activeTab.value !== 'profile')
const showCategoryHeader = computed(() => activeTab.value === 'equipment' && Boolean(selectedSport.value))
const hasMorePlaces = computed(() => places.value.length < placesTotal.value)
const hasMoreVenueSales = computed(() => venueSaleItems.value.length < venueSalesTotal.value)
const hasMoreEquipmentItems = computed(() => products.value.length < productsTotal.value)
const hasMoreSocial = computed(() => players.value.length < playersTotal.value || activities.value.length < activitiesTotal.value)
const hasMoreBlogs = computed(() => blogs.value.length < blogsTotal.value)
const hasMoreProfileBlogs = computed(() => profileBlogs.value.length < profileBlogsTotal.value)
const profileTitle = computed(() => profileMode.value === 'public' ? 'TA 的主页' : '我的')
const profileSubtitle = computed(() => profileMode.value === 'public' ? '球友主页' : '个人中心')
const profileAvatar = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.avatar : userProfile.value?.avatar)
const profileNickname = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.nickname : userProfile.value?.nickname)
const profileCity = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.city : userProfile.value?.city)
const profileLevel = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.level : userProfile.value?.level)
const profilePreferTime = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.preferTime : userProfile.value?.prefer_time)
const profileCreatedAt = computed(() => profileMode.value === 'public' ? viewedUserProfile.value?.createdAt : userProfile.value?.created_at)
const paidStatus = (status: string) => ['已支付', '已抢到', '已使用'].includes(status)
const visibleVenueOrders = computed(() => profileView.value === 'paid'
  ? venueOrders.value.filter((order) => paidStatus(order.status))
  : venueOrders.value)
const visibleEquipmentOrders = computed(() => profileView.value === 'paid'
  ? equipmentOrders.value.filter((order) => paidStatus(order.status))
  : equipmentOrders.value)
const visibleSeckillOrders = computed(() => profileView.value === 'paid'
  ? seckillOrders.value.filter((order) => paidStatus(order.status))
  : seckillOrders.value)
const profileVenueOrderCards = computed<ProfileOrderCard[]>(() => [
  ...visibleVenueOrders.value.map((order) => ({
    key: `venue-${order.id}`,
    title: order.productTitle,
    subtitle: order.venueName,
    meta: `${order.serviceDate} ${order.startTime.slice(0, 5)}-${order.endTime.slice(0, 5)}`,
    amount: order.amount,
    status: order.status,
    code: order.verifyCode
  })),
  ...visibleSeckillOrders.value
    .filter((order) => order.type === 1)
    .map((order) => ({
      key: `seckill-venue-${order.id}`,
      title: order.productName,
      subtitle: '秒杀场所',
      meta: formatDateTime(order.createdAt),
      amount: order.amount,
      status: order.status,
      code: `#${order.id}`
    }))
])
const profileEquipmentOrderCards = computed<ProfileOrderCard[]>(() => [
  ...visibleSeckillOrders.value
    .filter((order) => order.type === 2)
    .map((order) => ({
      key: `seckill-equipment-${order.id}`,
      title: order.productName,
      subtitle: '秒杀装备',
      meta: formatDateTime(order.createdAt),
      amount: order.amount,
      status: order.status,
      code: `#${order.id}`
    })),
  ...visibleEquipmentOrders.value.map((order) => ({
    key: `equipment-${order.id}`,
    title: equipmentOrderTitle(order),
    subtitle: order.address,
    meta: `${formatDateTime(order.createdAt)} · 共 ${equipmentOrderQuantity(order)} 件`,
    amount: order.totalAmount,
    status: order.status,
    code: `#${order.id}`
  }))
])
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
  phone: '13800000001',
  code: ''
})

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

const activityForm = reactive({
  sportCode: 'badminton',
  placeSource: 'amap',
  placeId: '',
  venueName: '先从场所列表选择',
  title: '今晚约一场',
  city: '西安',
  startTime: '2026-07-04T19:00',
  endTime: '2026-07-04T21:00',
  maxPlayers: 4,
  levelRequired: '中级',
  feeType: 'AA'
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

function resetListPaging() {
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
  venueOrders.value = []
  equipmentOrders.value = []
  seckillOrders.value = []
  cartItems.value = []
  profileBlogs.value = []
  viewedUserProfile.value = null
  selectedBlog.value = null
  cartCount.value = 0
  cartLoaded.value = false
  ordersLoaded.value = false
  profileView.value = 'orders'
  profileMode.value = 'me'
  publicProfileReturnTab.value = 'seckill'
  profileOrderPageVisible.value = false
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
      body: JSON.stringify({ phone: loginForm.phone })
    })
    if (result.code) {
      loginForm.code = result.code
    }
    startCodeCountdown(result.expireSeconds)
    message.value = result.code ? `验证码 ${result.code} 已发送，2 分钟内有效` : '验证码已发送'
  })
}

async function login() {
  await wrap(async () => {
    const result = await api<{ token: string }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(loginForm)
    })
    setToken(result.token)
    authToken.value = result.token
    activeTab.value = authReturnTab.value
    authPageVisible.value = false
    await loadCurrentTab()
  }, '登录成功')
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

function placeIndex(place: Place) {
  return places.value.findIndex((item) => item.id === place.id)
}

function placeRankFor(place: Place) {
  const index = Math.max(0, placeIndex(place))
  const sportCode = selectedSport.value || place.sportCode
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
  return places.value[(Math.max(1, rank) - 1) % places.value.length] || null
}

function productWithPlaceContext(product: VenueItem, place?: Place | null) {
  if (!place) return product
  return {
    ...product,
    amapPlaceId: place.id,
    venueName: place.name
  }
}

function setTrimmedParam(params: URLSearchParams, key: string, value?: string) {
  const text = value?.trim()
  if (text) params.set(key, text)
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
  const result = await api<PageResult<Place>>(`/api/places/nearby?${params}`)
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
  if (blogChannel.value === 'follow' && !loggedIn.value) {
    blogs.value = []
    blogsPage.value = 1
    blogsTotal.value = 0
    return
  }
  const params = new URLSearchParams({
    channel: blogChannel.value === 'follow' ? 'follow' : 'recommend'
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
  venueOrders.value = await api<VenueOrder[]>('/api/orders/1')
}

async function loadEquipmentOrders() {
  if (!loggedIn.value) {
    equipmentOrders.value = []
    return
  }
  equipmentOrders.value = await api<EquipmentOrder[]>('/api/orders/2')
}

async function loadSeckillOrders() {
  if (!loggedIn.value) {
    seckillOrders.value = []
    return
  }
  const [venueResult, equipmentResult] = await Promise.all([
    api<SeckillOrder[]>('/api/seckill/1/orders'),
    api<SeckillOrder[]>('/api/seckill/2/orders')
  ])
  seckillOrders.value = [...venueResult, ...equipmentResult]
}

async function loadOrderBundle(force = false) {
  if (!loggedIn.value) {
    venueOrders.value = []
    equipmentOrders.value = []
    seckillOrders.value = []
    ordersLoaded.value = false
    return
  }
  if (ordersLoaded.value && !force) return
  await Promise.all([loadVenueOrders(), loadEquipmentOrders(), loadSeckillOrders()])
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
  if (nearPageBottom()) {
    void loadMoreCurrentTab()
  }
}

async function switchTab(tab: Tab) {
  activeTab.value = tab
  selectedBlog.value = null
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
    selectedBlog.value = await api<BlogPost>(`/api/blogs/${blog.id}`)
  })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function closeBlogDetail() {
  selectedBlog.value = null
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
    productQuery.keyword = blog.relatedTitle
    productsPage.value = 1
    productsTotal.value = 0
    await wrap(async () => {
      await Promise.all([loadEquipmentItems(), loadSeckill()])
    })
    return
  }
  await openVenueSalePage('', '场馆团购', '来自社区动态的关联项目')
}

async function openProfileView(view: ProfileView) {
  profileView.value = view
  profileOrderPageVisible.value = true
  await wrap(() => loadProfileViewData(view))
}

function closeProfileOrderPage() {
  profileOrderPageVisible.value = false
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

async function useCurrentLocation() {
  await wrap(async () => {
    locating.value = true
    try {
      const located = await locateWithAmapFirst()
      const accuracy = Math.round(located.accuracy)
      locationAccuracy.value = accuracy
      placeQuery.lng = located.lng
      placeQuery.lat = located.lat
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
      await loadCurrentTab()
      const sourceLabel = located.source === 'amap' ? '高德高精度定位' : '浏览器定位'
      message.value = accuracy > 1000
        ? `已使用当前位置（${sourceLabel}），定位精度约 ${accuracy} 米；当前可能仍是电脑/Wi-Fi 网络定位`
        : `已使用当前位置（${sourceLabel}），定位精度约 ${accuracy} 米`
    } catch (error) {
      throw new Error(geolocationMessage(error))
    } finally {
      locating.value = false
    }
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

async function showSocial() {
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

function equipmentOrderTitle(order: EquipmentOrder) {
  return order.items?.length ? order.items.map((item) => item.productName).join('、') : '装备订单'
}

function equipmentOrderQuantity(order: EquipmentOrder) {
  return order.items?.reduce((sum, item) => sum + item.quantity, 0) || 0
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

function typeClass(productType: string) {
  return {
    'type-time': productType === 'TIME_PACKAGE',
    'type-slot': productType === 'COURT_SLOT',
    'type-coach': productType === 'COACH_LESSON'
  }
}

onMounted(async () => {
  window.addEventListener('scroll', handleWindowScroll, { passive: true })
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
        <div class="auth-mark"><Zap :size="24" /></div>
        <h1>球动</h1>
        <p>约场、买课、找搭子</p>
      </div>

      <section class="auth-panel">
        <label class="auth-field">
          <span>手机号</span>
          <div class="auth-input">
            <Phone :size="18" />
            <input v-model="loginForm.phone" inputmode="tel" maxlength="11" placeholder="请输入手机号" />
          </div>
        </label>

        <label class="auth-field">
          <span>验证码</span>
          <div class="auth-code-row">
            <div class="auth-input">
              <MessageCircle :size="18" />
              <input v-model="loginForm.code" inputmode="numeric" maxlength="6" placeholder="请输入验证码" />
            </div>
            <button class="code-button" @click="sendLoginCode" :disabled="loading || codeCountdown > 0">
              {{ codeCountdown > 0 ? `${codeCountdown}s` : '获取验证码' }}
            </button>
          </div>
        </label>

        <button class="auth-submit" @click="login" :disabled="loading">
          <LogIn :size="18" />
          登录/注册
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
        <button v-else class="login-chip muted" @click="openAuthPage('profile')"><LogIn :size="15" /> 登录</button>
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
      <section v-if="selectedBlog" class="page-stack blog-detail-page">
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
              <img :src="selectedBlog.avatar || 'https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&w=240&q=80'" :alt="selectedBlog.nickname" />
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
            <img v-for="image in selectedBlog.images" :key="image" :src="image" :alt="selectedBlog.title" />
          </div>
          <h2>{{ selectedBlog.title }}</h2>
          <p>{{ selectedBlog.content }}</p>
          <button class="blog-related detail-related" type="button" @click="openBlogRelated(selectedBlog)">
            <ShoppingBag :size="15" />
            <span>{{ selectedBlog.relatedTitle }}</span>
            <strong v-if="selectedBlog.relatedPrice">{{ yuan(selectedBlog.relatedPrice) }}</strong>
          </button>
          <div class="blog-detail-actions">
            <button type="button" class="like-mini" :class="{ active: selectedBlog.isLiked }" @click="toggleBlogLike(selectedBlog)">
              <Heart :size="17" />
              {{ selectedBlog.liked }}
            </button>
            <span>{{ formatDateTime(selectedBlog.createdAt) }}</span>
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
              :src="selectedPlace.coverUrl || 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=80'"
              :alt="selectedPlace.name"
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
                :src="item.coverUrl || selectedPlace.coverUrl || 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=80'"
                :alt="item.title"
              />
              <div class="detail-deal-main">
                <span class="service-type" :class="typeClass(item.productType)">{{ item.productTypeName }}</span>
                <h3>{{ item.title }}</h3>
                <p>{{ inventoryText(item) }} · {{ item.purchasable ? '可购买' : '已售罄' }}</p>
                <small>{{ item.useRule }}</small>
                <div class="detail-price-line">
                  <strong>{{ yuan(item.price) }}</strong>
                  <span v-if="item.originalPrice">{{ yuan(item.originalPrice) }}</span>
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
              <img class="review-avatar" :src="review.avatar" :alt="review.nickname" />
              <div>
                <div class="review-head">
                  <h3>{{ review.nickname }}</h3>
                  <small>{{ reviewDate(review.createdAt) }}</small>
                </div>
                <p class="review-stars">{{ '★★★★★'.slice(0, Math.round(review.rating || 5)) }} 超赞</p>
                <p>{{ review.content }}</p>
                <div v-if="reviewImages(review).length" class="review-images">
                  <img v-for="image in reviewImages(review).slice(0, 3)" :key="image" :src="image" :alt="review.nickname" />
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
            <img :src="place.coverUrl || 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=80'" :alt="place.name" />
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
                <span v-if="item.originalPrice">{{ yuan(item.originalPrice || 0) }}</span>
                <button class="primary" @click="buyVenueItem(item)" :disabled="buyingVenueItemId === item.id">
                  {{ buyingVenueItemId === item.id ? '购买中' : '购买' }}
                </button>
              </div>
            </article>
          </section>
          </template>
          <div v-if="loadingMore" class="load-more-state">正在加载更多场所</div>
          <div v-else-if="places.length && !hasMorePlaces" class="load-more-state muted-state">已经到底了</div>
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
          <article v-for="item in venueSaleItems" :key="item.id" class="venue-sale-row">
            <img :src="item.coverUrl || 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=80'" :alt="item.title" />
            <div>
              <div class="sale-row-head">
                <span class="service-type" :class="typeClass(item.productType)">{{ item.productTypeName }}</span>
                <small>{{ item.purchasable ? '可购买' : '已售罄' }}</small>
              </div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.venueName }}</p>
              <small>{{ item.description }}</small>
              <div class="tag-row">
                <span v-for="tag in item.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
              </div>
              <div class="sale-row-bottom">
                <div>
                  <strong>{{ yuan(item.price) }}</strong>
                  <span v-if="item.originalPrice">{{ yuan(item.originalPrice) }}</span>
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
          <button class="ghost" @click="() => loadBlogs()"><Heart :size="16" /> 刷新</button>
        </div>

        <div v-if="!blogs.length" class="empty-box">暂无社区动态</div>
        <div v-else class="blog-masonry">
          <article v-for="blog in blogs" :key="blog.id" class="blog-card clickable-card" @click="openBlogDetail(blog)">
            <button class="blog-cover-button" type="button" @click.stop="openBlogDetail(blog)">
              <img
                :src="blog.images[0] || blog.relatedCoverUrl || 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=80'"
                :alt="blog.title"
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
                  <img :src="blog.avatar || 'https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&w=240&q=80'" :alt="blog.nickname" />
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
          <img :src="player.avatar" :alt="player.nickname" />
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
            <img :src="activity.coverUrl" :alt="activity.productName" />
            <div>
              <span>{{ activity.categoryName }}</span>
              <h3>{{ activity.productName }}</h3>
              <p>一人一单 · Redis Lua 扣减</p>
              <strong>{{ yuan(activity.seckillPrice) }}</strong>
              <em>{{ yuan(activity.originalPrice) }}</em>
            </div>
            <button class="primary" @click="submitSeckill(activity.id)" :disabled="!activity.purchasable">抢购</button>
          </article>
        </section>
        <article v-for="product in products" :key="product.id" class="product-row">
          <img :src="product.coverUrl" :alt="product.name" />
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
              :src="profileAvatar || 'https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&w=240&q=80'"
              :alt="profileNickname || '用户头像'"
            />
            <div>
              <h3>{{ profileNickname || '球友' }}</h3>
              <p>{{ profileMode === 'me' ? maskedPhone(userProfile?.phone) : '公开主页' }}</p>
              <div class="profile-tags">
                <span>{{ profileCity || locationLabel }}</span>
                <span>{{ profileLevel || '新手' }}</span>
              </div>
            </div>
            <button v-if="profileMode === 'me'" class="ghost" @click="logout">退出</button>
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

        <section v-if="!profileOrderPageVisible && (profileMode === 'public' || loggedIn)" class="profile-blogs">
          <div class="section-title compact-title">
            <div>
              <span>{{ profileMode === 'public' ? 'TA 的' : '我的' }}</span>
              <strong>动态</strong>
            </div>
          </div>
          <div v-if="!profileBlogs.length" class="empty-box">暂无动态</div>
          <div v-else class="blog-masonry">
            <article v-for="blog in profileBlogs" :key="blog.id" class="blog-card clickable-card" @click="openBlogDetail(blog)">
              <button class="blog-cover-button" type="button" @click.stop="openBlogDetail(blog)">
                <img
                  :src="blog.images[0] || blog.relatedCoverUrl || 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=80'"
                  :alt="blog.title"
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
                    <img :src="blog.avatar || 'https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&w=240&q=80'" :alt="blog.nickname" />
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
            <img :src="item.coverUrl" :alt="item.productName" />
            <div>
              <h3>{{ item.productName }}</h3>
              <p>{{ item.brand }}</p>
              <small>{{ item.meta }} · {{ yuan(item.price) }} × {{ item.quantity }}</small>
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
