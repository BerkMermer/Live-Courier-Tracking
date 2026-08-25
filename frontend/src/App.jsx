import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import LiveMap from './components/LiveMap';
import DashboardPanel from './components/DashboardPanel';
import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';
import { haversineKm, etaMinutes, formatKm } from './utils/geo';
import { useRoadRoute } from './hooks/useRoadRoute';

const API_BASE = import.meta.env.VITE_API_BASE ?? (import.meta.env.PROD ? '' : 'http://localhost:8080');

function App() {
  const [email, setEmail] = useState('demo-153357@example.com');
  const [password, setPassword] = useState('securePass123');
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

  // Sipariş durumunu periyodik yenile (Swagger'dan assign görünür olsun)
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
    const crowDist = haversineKm(
      courierLocation?.lat,
      courierLocation?.lng,
      order?.pickupLatitude,
      order?.pickupLongitude
    );
    const dist = roadRoute.distanceKm ?? crowDist;
    const eta =
      roadRoute.durationMin != null
        ? roadRoute.durationMin
        : etaMinutes(crowDist);
    return {
      distanceLabel: formatKm(dist),
      etaLabel: eta == null ? '—' : `~${eta} dk`,
      coordsLabel:
        courierLocation != null
          ? `${courierLocation.lat.toFixed(5)}, ${courierLocation.lng.toFixed(5)}`
          : null,
      lastUpdateLabel: courierLocation?.updatedAt
        ? new Date(courierLocation.updatedAt).toLocaleTimeString('tr-TR', { hour12: false })
        : null,
    };
  }, [
    courierLocation,
    order?.pickupLatitude,
    order?.pickupLongitude,
    roadRoute.distanceKm,
    roadRoute.durationMin,
  ]);

  if (!token) {
    return (
      <div className="min-h-screen w-full bg-gray-950 text-white flex items-center justify-center p-6">
        <form
          onSubmit={handleLogin}
          className="w-full max-w-md bg-white/5 border border-white/10 rounded-2xl p-8 space-y-4 shadow-2xl"
        >
          <div>
            <h1 className="text-2xl font-bold">Kurye Takip</h1>
            <p className="text-sm text-gray-400 mt-1">
              Giriş yap; Swagger’dan attığın istekler burada canlı görünsün.
            </p>
          </div>
          <label className="block text-sm">
            <span className="text-gray-400">Email</span>
            <input
              className="mt-1 w-full rounded-lg bg-gray-900 border border-gray-700 px-3 py-2"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              type="email"
              required
            />
          </label>
          <label className="block text-sm">
            <span className="text-gray-400">Şifre</span>
            <input
              className="mt-1 w-full rounded-lg bg-gray-900 border border-gray-700 px-3 py-2"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              required
            />
          </label>
          {loginError && <p className="text-sm text-rose-400">{loginError}</p>}
          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-blue-600 hover:bg-blue-500 py-2.5 font-semibold disabled:opacity-50"
          >
            {loading ? 'Giriş...' : 'Giriş yap'}
          </button>
          <p className="text-xs text-gray-500">
            Demo müşteri: demo-153357@example.com / securePass123
          </p>
        </form>
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
      }
    : {
        orderNo: '—',
        customerName: '—',
        status: 'YOK',
        pickupAddress: '—',
        deliveryAddress: '—',
      };

  const courierInfo = {
    id: order?.courierId ?? null,
    name: order?.courierName || courierLocation?.name || 'Atanmadı',
    plate: order?.courierId ? 'Profil #' + order.courierId : '—',
    phone: '—',
  };

  return (
    <div className="flex h-screen w-full bg-slate-950 overflow-hidden text-white font-sans">
      <div className="w-2/3 h-full relative">
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

      <div className="w-1/3 h-full p-5 flex flex-col gap-3 z-20 bg-[#f4f6f8] border-l border-slate-200 shadow-[-8px_0_24px_rgba(15,23,42,0.06)]">
        <div className="flex gap-2">
          <button
            type="button"
            onClick={refreshOrders}
            className="flex-1 rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 py-2 text-sm font-medium shadow-sm"
          >
            Yenile
          </button>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-xl bg-slate-900 hover:bg-slate-800 text-white px-4 py-2 text-sm font-medium"
          >
            Çıkış
          </button>
        </div>
        <DashboardPanel
          order={orderInfo}
          orders={orders}
          selectedTracking={selectedTracking}
          onSelectOrder={selectOrder}
          courier={courierInfo}
          wsConnected={wsConnected}
          lastUpdateLabel={trackingMetrics.lastUpdateLabel}
        />
      </div>
    </div>
  );
}

export default App;
