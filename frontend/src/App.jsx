import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import LiveMap from './components/LiveMap';
import DashboardPanel from './components/DashboardPanel';
import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';
import { ArrowRight, LockKeyhole, LogOut, Mail, MapPinned, RefreshCw } from 'lucide-react';
import { formatKm } from './utils/geo';
import { useRoadRoute } from './hooks/useRoadRoute';

const API_BASE = import.meta.env.VITE_API_BASE ?? (import.meta.env.PROD ? '' : 'http://localhost:8080');

function App() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [token, setToken] = useState(() => localStorage.getItem('cta_token') || '');
  const [orders, setOrders] = useState([]);
  const [selectedTracking, setSelectedTracking] = useState(null);
  const [courierLocation, setCourierLocation] = useState(null);
  const [events, setEvents] = useState([]);
  const [wsConnected, setWsConnected] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [loading, setLoading] = useState(false);
  const stompRef = useRef(null);
  const selectedTrackingRef = useRef(selectedTracking);
  selectedTrackingRef.current = selectedTracking;

  const order = useMemo(() => {
    if (!orders.length) return null;
    return orders.find((o) => o.trackingNumber === selectedTracking) || orders[0];
  }, [orders, selectedTracking]);

  const addEvent = useCallback((msg) => {
    setEvents((prev) => {
      const timeStr = new Date().toLocaleTimeString('tr-TR', { hour12: false });
      return [`[${timeStr}] ${msg}`, ...prev].slice(0, 12);
    });
  }, []);

  const loadCourierLocation = useCallback(async (jwt, courierId) => {
    if (!courierId) {
      setCourierLocation(null);
      return;
    }
    const locRes = await fetch(`${API_BASE}/api/v1/couriers/${courierId}/location`, {
      headers: { Authorization: `Bearer ${jwt}`, Accept: 'application/json' },
    });
    if (!locRes.ok) return;
    const loc = await locRes.json();
    if (loc.latitude != null && loc.longitude != null) {
      setCourierLocation({
        lat: loc.latitude,
        lng: loc.longitude,
        name: loc.fullName,
        updatedAt: loc.lastLocationUpdate || Date.now(),
      });
    }
  }, []);

  const loadOrders = useCallback(
    async (jwt) => {
      const res = await fetch(`${API_BASE}/api/v1/orders/me`, {
        headers: { Authorization: `Bearer ${jwt}`, Accept: 'application/json' },
      });
      if (!res.ok) throw new Error(`Siparişler alınamadı (${res.status})`);
      const list = await res.json();
      setOrders(list);

      const prev = selectedTrackingRef.current;
      const stillExists = list.some((o) => o.trackingNumber === prev);
      const next = stillExists ? prev : list[0]?.trackingNumber || null;
      setSelectedTracking(next);

      const chosen = list.find((o) => o.trackingNumber === next) || null;
      await loadCourierLocation(jwt, chosen?.courierId);
      return chosen;
    },
    [loadCourierLocation]
  );

  const selectOrder = async (trackingNumber) => {
    setSelectedTracking(trackingNumber);
    const chosen = orders.find((o) => o.trackingNumber === trackingNumber);
    if (token) {
      await loadCourierLocation(token, chosen?.courierId);
    }
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoginError('');
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || `Giriş başarısız (${res.status})`);
      }
      const data = await res.json();
      localStorage.setItem('cta_token', data.token);
      setToken(data.token);
      addEvent(`Giriş OK: ${data.user.email} (${data.user.role})`);
      await loadOrders(data.token);
    } catch (err) {
      setLoginError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('cta_token');
    setToken('');
    setOrders([]);
    setSelectedTracking(null);
    setCourierLocation(null);
    setWsConnected(false);
    if (stompRef.current) {
      stompRef.current.deactivate();
      stompRef.current = null;
    }
    addEvent('Çıkış yapıldı.');
  };

  const refreshOrders = async () => {
    if (!token) return;
    try {
      await loadOrders(token);
      addEvent('Siparişler yenilendi.');
    } catch (err) {
      addEvent(`Yenileme hatası: ${err.message}`);
    }
  };

  // JWT ile STOMP — siparişte kurye varsa konum topic'ine abone ol
  useEffect(() => {
    if (!token || !order?.courierId) {
      setWsConnected(false);
      return undefined;
    }

    const courierId = order.courierId;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws-courier`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: () => {},
    });

    client.onConnect = () => {
      setWsConnected(true);
      addEvent(`WebSocket bağlandı — /topic/courier-location.${courierId}`);
      client.subscribe(`/topic/courier-location.${courierId}`, (message) => {
        if (!message.body) return;
        const locData = JSON.parse(message.body);
        setCourierLocation({
          lat: locData.latitude,
          lng: locData.longitude,
          name: locData.fullName,
          updatedAt: locData.lastLocationUpdate || Date.now(),
        });
        addEvent(
          `Canlı konum: ${Number(locData.latitude).toFixed(4)}, ${Number(locData.longitude).toFixed(4)}`
        );
      });
    };

    client.onStompError = (frame) => {
      setWsConnected(false);
      addEvent(`Broker hatası: ${frame.headers.message || 'unknown'}`);
    };

    client.onWebSocketClose = () => setWsConnected(false);

    stompRef.current = client;
    client.activate();

    return () => {
      client.deactivate();
      stompRef.current = null;
      setWsConnected(false);
    };
  }, [token, order?.courierId, addEvent]);

  // Sayfa açılışında kayıtlı token varsa siparişleri yükle
  useEffect(() => {
    if (!token) return;
    loadOrders(token).catch((err) => {
      addEvent(`Oturum geçersiz: ${err.message}`);
      handleLogout();
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Sipariş durumunu periyodik olarak güncelle
  useEffect(() => {
    if (!token) return undefined;
    const id = setInterval(() => {
      loadOrders(token).catch(() => {});
    }, 8000);
    return () => clearInterval(id);
  }, [token, loadOrders]);

  const roadRoute = useRoadRoute(
    courierLocation?.lat,
    courierLocation?.lng,
    order?.pickupLatitude,
    order?.pickupLongitude
  );

  const trackingMetrics = useMemo(() => {
    const dist = roadRoute.distanceKm;
    const eta = roadRoute.durationMin;
    return {
      distanceLabel: formatKm(dist),
      etaLabel: eta == null ? '—' : `~${eta} dk`,
      coordsLabel:
        courierLocation != null
          ? `${courierLocation.lat.toFixed(5)}, ${courierLocation.lng.toFixed(5)}`
          : null,
      lastUpdateLabel: courierLocation?.updatedAt
        ? formatTimestamp(courierLocation.updatedAt)
        : null,
    };
  }, [
    courierLocation,
    roadRoute.distanceKm,
    roadRoute.durationMin,
  ]);

  if (!token) {
    return (
      <div className="min-h-screen bg-slate-50 px-5 py-8 text-slate-900 sm:px-8 lg:grid lg:place-items-center">
        <main className="mx-auto grid w-full max-w-5xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.09)] lg:grid-cols-[0.9fr_1.1fr]">
          <section className="hidden border-r border-blue-100 bg-blue-50 p-10 lg:flex lg:flex-col lg:justify-between">
            <div>
              <div className="inline-flex items-center gap-2 text-sm font-semibold text-blue-800">
                <span className="grid h-9 w-9 place-items-center rounded-lg bg-white text-blue-700 ring-1 ring-blue-100">
                  <MapPinned size={19} />
                </span>
                Kurye Takip
              </div>
              <h1 className="mt-16 max-w-sm text-4xl font-semibold leading-tight tracking-tight text-slate-950">
                Teslimatınızı anlık olarak takip edin.
              </h1>
              <p className="mt-5 max-w-sm text-base leading-relaxed text-slate-600">
                Kurye konumu, yol rotası ve tahmini varış süresi tek ekranda güvenle güncellenir.
              </p>
            </div>
            <div className="mt-16 border-t border-blue-100 pt-6 text-sm text-slate-500">
              Güvenli oturum <span aria-hidden>•</span> Canlı konum <span aria-hidden>•</span>{' '}
              Güncel teslimat bilgisi
            </div>
          </section>

          <form onSubmit={handleLogin} className="p-7 sm:p-10 lg:p-14">
            <div className="flex items-center gap-2 text-sm font-semibold text-blue-800 lg:hidden">
              <span className="grid h-9 w-9 place-items-center rounded-lg bg-blue-50 text-blue-700 ring-1 ring-blue-100">
                <MapPinned size={19} />
              </span>
              Kurye Takip
            </div>
            <p className="mt-10 text-xs font-semibold uppercase tracking-[0.14em] text-blue-600 lg:mt-0">
              Müşteri paneli
            </p>
            <h2 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">Tekrar hoş geldiniz</h2>
            <p className="mt-3 text-sm leading-relaxed text-slate-500">
              Aktif siparişinizi ve kuryenizin canlı konumunu görüntülemek için giriş yapın.
            </p>

            <div className="mt-8 space-y-5">
              <label className="block text-sm font-medium text-slate-700">
                E-posta adresi
                <span className="relative mt-2 block">
                  <Mail
                    size={17}
                    className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                  />
                  <input
                    className="w-full rounded-lg border border-slate-300 bg-white py-3 pl-10 pr-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    type="email"
                    autoComplete="email"
                    placeholder="mert.kaya@example.com"
                    required
                  />
                </span>
              </label>

              <label className="block text-sm font-medium text-slate-700">
                Şifre
                <span className="relative mt-2 block">
                  <LockKeyhole
                    size={17}
                    className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                  />
                  <input
                    className="w-full rounded-lg border border-slate-300 bg-white py-3 pl-10 pr-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    type="password"
                    autoComplete="current-password"
                    placeholder="Şifrenizi girin"
                    required
                  />
                </span>
              </label>
            </div>

            {loginError && (
              <p role="alert" className="mt-5 rounded-lg bg-rose-50 px-3 py-2.5 text-sm text-rose-700">
                {loginError}
              </p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="mt-7 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-3 text-sm font-semibold text-white transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:cursor-wait disabled:opacity-60"
            >
              {loading ? 'Giriş yapılıyor…' : 'Giriş yap'}
              {!loading && <ArrowRight size={17} />}
            </button>

            <p className="mt-6 text-center text-xs leading-relaxed text-slate-400">
              Giriş bilgileriniz yalnızca güvenli oturum oluşturmak için kullanılır.
            </p>
          </form>
        </main>
      </div>
    );
  }

  const orderInfo = order
    ? {
        orderNo: order.trackingNumber,
        customerName: order.customerName,
        status: order.status,
        pickupAddress: order.pickupAddress,
        deliveryAddress: order.deliveryAddress,
        createdAt: order.createdAt,
      }
    : {
        orderNo: '—',
        customerName: '—',
        status: 'YOK',
        pickupAddress: '—',
        deliveryAddress: '—',
        createdAt: null,
      };

  const courierInfo = {
    id: order?.courierId ?? null,
    name: order?.courierName || courierLocation?.name || 'Atanmadı',
    plate: order?.courierVehiclePlate || null,
    phone: order?.courierPhoneMasked || null,
  };

  return (
    <main className="flex min-h-screen w-full flex-col overflow-x-hidden bg-slate-100 font-sans text-slate-900 lg:h-screen lg:flex-row lg:overflow-hidden">
      <div className="relative h-[48vh] min-h-[360px] w-full border-b border-slate-200 lg:h-full lg:min-h-0 lg:w-[65%] lg:border-b-0 lg:border-r">
        <LiveMap
          courierLocation={courierLocation}
          courierName={courierInfo.name}
          pickup={[order?.pickupLatitude, order?.pickupLongitude]}
          distanceLabel={trackingMetrics.distanceLabel}
          etaLabel={trackingMetrics.etaLabel}
          live={wsConnected}
          routePositions={roadRoute.positions}
          snappedCourier={roadRoute.snappedFrom}
        />
      </div>

      <aside className="z-20 flex w-full flex-col gap-4 bg-slate-50 p-4 sm:p-5 lg:h-full lg:w-[35%] lg:min-w-[390px] lg:max-w-[560px]">
        <div className="flex items-center justify-between gap-3 border-b border-slate-200 pb-4">
          <div className="flex min-w-0 items-center gap-2.5">
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-blue-600 text-white">
              <MapPinned size={19} />
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-slate-950">Kurye Takip</p>
              <p className="truncate text-xs text-slate-500">Canlı teslimat merkezi</p>
            </div>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={refreshOrders}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 shadow-sm transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <RefreshCw size={14} />
              <span className="hidden sm:inline">Yenile</span>
            </button>
            <button
              type="button"
              onClick={handleLogout}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 shadow-sm transition hover:border-rose-200 hover:bg-rose-50 hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <LogOut size={14} />
              <span className="hidden sm:inline">Çıkış</span>
            </button>
          </div>
        </div>
        <DashboardPanel
          order={orderInfo}
          orders={orders}
          selectedTracking={selectedTracking}
          onSelectOrder={selectOrder}
          courier={courierInfo}
          wsConnected={wsConnected}
          lastUpdateLabel={trackingMetrics.lastUpdateLabel}
          distanceLabel={trackingMetrics.distanceLabel}
          etaLabel={trackingMetrics.etaLabel}
        />
      </aside>
    </main>
  );
}

function formatTimestamp(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat('tr-TR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date);
}

export default App;
