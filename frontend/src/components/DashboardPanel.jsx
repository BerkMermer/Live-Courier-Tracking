import { useState } from 'react';
import {
  Bike,
  Check,
  Clock3,
  Copy,
  MessageSquare,
  Navigation,
  Route,
  UserRound,
} from 'lucide-react';
import ContactModal from './ContactModal';
import { formatApiTimestamp } from '../utils/geo';

const STATUS_TR = {
  PENDING: { label: 'Beklemede', tone: 'amber' },
  ASSIGNED: { label: 'Alışa gidiyor', tone: 'sky' },
  PICKED_UP: { label: 'Teslimata gidiyor', tone: 'sky' },
  DELIVERED: { label: 'Teslim edildi', tone: 'emerald' },
  CANCELLED: { label: 'İptal edildi', tone: 'rose' },
  YOK: { label: 'Aktif sipariş yok', tone: 'slate' },
};

const TONE = {
  amber: 'bg-amber-50 text-amber-800 ring-amber-200',
  sky: 'bg-sky-50 text-sky-800 ring-sky-200',
  emerald: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  rose: 'bg-rose-50 text-rose-800 ring-rose-200',
  slate: 'bg-slate-100 text-slate-600 ring-slate-200',
};

const DashboardPanel = ({
  order,
  orders = [],
  selectedTracking,
  onSelectOrder,
  courier,
  wsConnected,
  lastUpdateLabel,
  distanceLabel,
  etaLabel,
}) => {
  const [contactOpen, setContactOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const statusMeta = STATUS_TR[order.status] || {
    label: order.status,
    tone: 'slate',
  };

  const copyTrackingNumber = async () => {
    if (!order.orderNo || order.orderNo === '—') return;
    try {
      await navigator.clipboard.writeText(order.orderNo);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };

  return (
    <>
      <div className="flex h-full flex-col gap-4 overflow-y-auto pr-1 text-slate-800 custom-scrollbar">
        <header className="flex items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-blue-600">
              Canlı teslimat
            </p>
            <h2 className="mt-1 text-xl font-semibold tracking-tight text-slate-950">
              Sipariş takibi
            </h2>
            <p className="mt-1 text-xs leading-relaxed text-slate-500">
              {lastUpdateLabel ? `Son konum: ${lastUpdateLabel}` : 'Kurye konumu bekleniyor'}
            </p>
          </div>
          <span
            className={`inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-[11px] font-semibold ${
              wsConnected
                ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                : 'border-slate-200 bg-slate-50 text-slate-500'
            }`}
          >
            <span
              className={`h-1.5 w-1.5 rounded-full ${
                wsConnected ? 'bg-emerald-500' : 'bg-slate-400'
              }`}
            />
            {wsConnected ? 'Canlı' : 'Bekliyor'}
          </span>
        </header>

        <section className="rounded-xl border border-slate-200 bg-white shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
          <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-4 py-3.5">
            <div className="min-w-0">
              <p className="text-xs font-medium text-slate-500">Sipariş kodu</p>
              <div className="mt-1 flex items-center gap-2">
                <span className="truncate font-mono text-sm font-semibold text-slate-900">
                  #{shortId(order.orderNo)}
                </span>
                <button
                  type="button"
                  onClick={copyTrackingNumber}
                  className="rounded-md p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  aria-label="Sipariş kodunu kopyala"
                  title="Tam sipariş kodunu kopyala"
                >
                  {copied ? <Check size={15} className="text-emerald-600" /> : <Copy size={15} />}
                </button>
              </div>
              <p className="mt-1 text-[11px] text-slate-400">
                Oluşturuldu: {formatApiTimestamp(order.createdAt)}
              </p>
            </div>
            <span
              className={`shrink-0 rounded-md px-2.5 py-1 text-[11px] font-semibold ring-1 ${
                TONE[statusMeta.tone]
              }`}
            >
              {statusMeta.label}
            </span>
          </div>

          <div className="grid grid-cols-2 divide-x divide-slate-100">
            <Metric icon={Navigation} label="Alışa kalan" value={distanceLabel || '—'} />
            <Metric icon={Clock3} label="Tahmini varış" value={etaLabel || '—'} />
          </div>
        </section>

        <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Route size={16} className="text-blue-600" />
              <h3 className="text-sm font-semibold text-slate-900">Teslimat rotası</h3>
            </div>
            <span className="text-xs font-medium text-slate-400">{progressLabel(order.status)}</span>
          </div>

          <div className="mt-5">
            <div className="relative mx-2 h-1 rounded-full bg-slate-100">
              <span
                className="absolute inset-y-0 left-0 rounded-full bg-blue-600 transition-all duration-500"
                style={{ width: `${progressPercent(order.status)}%` }}
              />
              <span className="absolute left-0 top-1/2 h-3.5 w-3.5 -translate-y-1/2 rounded-full border-[3px] border-white bg-blue-600 shadow-sm" />
              <span
                className={`absolute right-0 top-1/2 h-3.5 w-3.5 -translate-y-1/2 rounded-full border-[3px] border-white shadow-sm ${
                  order.status === 'DELIVERED' ? 'bg-emerald-500' : 'bg-slate-300'
                }`}
              />
            </div>
            <div className="mt-3 grid grid-cols-2 gap-6">
              <RouteStop label="Alış" address={order.pickupAddress} />
              <RouteStop label="Teslimat" address={order.deliveryAddress} align="right" />
            </div>
          </div>
        </section>

        <section className="rounded-xl border border-slate-200 bg-white shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
          <div className="flex items-center gap-3 border-b border-slate-100 px-4 py-3">
            <div className="grid h-9 w-9 place-items-center rounded-lg bg-slate-50 text-slate-600 ring-1 ring-slate-200">
              <UserRound size={17} />
            </div>
            <div className="min-w-0">
              <p className="text-xs text-slate-500">Müşteri</p>
              <p className="truncate text-sm font-semibold text-slate-900">{order.customerName}</p>
            </div>
          </div>

          <div className="p-4">
            <div className="flex items-center gap-3">
              <div className="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-blue-50 text-blue-700 ring-1 ring-blue-100">
                <Bike size={20} />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold text-slate-950">{courier.name}</p>
                <p className="mt-0.5 truncate text-sm text-slate-500">
                  Motosiklet <span aria-hidden>•</span> {courier.plate || 'Plaka bilgisi yok'}
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setContactOpen(true)}
              disabled={!courier.id}
              className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-45"
            >
              <MessageSquare size={16} />
              İletişime geç
            </button>
          </div>
        </section>

        <section className="rounded-xl border border-slate-200 bg-white shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
          <div className="border-b border-slate-100 px-4 py-3">
            <h3 className="text-sm font-semibold text-slate-900">Sipariş geçmişi</h3>
            <p className="mt-0.5 text-xs text-slate-400">Haritada görüntülemek için bir sipariş seç</p>
          </div>
          {orders.length === 0 ? (
            <p className="px-4 py-6 text-sm text-slate-400">Henüz sipariş yok</p>
          ) : (
            <ul className="max-h-44 divide-y divide-slate-100 overflow-y-auto">
              {orders.map((item) => {
                const active = item.trackingNumber === selectedTracking;
                const meta = STATUS_TR[item.status] || { label: item.status, tone: 'slate' };
                return (
                  <li key={item.trackingNumber}>
                    <button
                      type="button"
                      onClick={() => onSelectOrder?.(item.trackingNumber)}
                      className={`w-full px-4 py-3 text-left transition-colors ${
                        active ? 'bg-blue-50/70' : 'hover:bg-slate-50'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-mono text-[11px] text-slate-500">
                          {shortId(item.trackingNumber)}
                        </span>
                        <span
                          className={`rounded-md px-2 py-0.5 text-[10px] font-semibold ring-1 ${
                            TONE[meta.tone]
                          }`}
                        >
                          {meta.label}
                        </span>
                      </div>
                      <p className="mt-1 truncate text-sm text-slate-700">
                        {item.pickupAddress} → {item.deliveryAddress}
                      </p>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>

      {contactOpen && <ContactModal courier={courier} onClose={() => setContactOpen(false)} />}
    </>
  );
};

function Metric({ icon: Icon, label, value }) {
  return (
    <div className="px-4 py-3.5">
      <div className="flex items-center gap-1.5 text-slate-500">
        <Icon size={14} />
        <span className="text-[11px] font-medium">{label}</span>
      </div>
      <p className="mt-1 text-lg font-semibold tabular-nums text-slate-950">{value}</p>
    </div>
  );
}

function RouteStop({ label, address, align = 'left' }) {
  return (
    <div className={align === 'right' ? 'text-right' : ''}>
      <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-xs font-medium leading-relaxed text-slate-800">{address}</p>
    </div>
  );
}

function progressPercent(status) {
  return {
    PENDING: 0,
    ASSIGNED: 35,
    PICKED_UP: 72,
    DELIVERED: 100,
    CANCELLED: 0,
  }[status] ?? 0;
}

function progressLabel(status) {
  return {
    PENDING: 'Kurye bekleniyor',
    ASSIGNED: 'Alış aşaması',
    PICKED_UP: 'Teslimat aşaması',
    DELIVERED: 'Tamamlandı',
    CANCELLED: 'İptal edildi',
  }[status] ?? status;
}

function shortId(id) {
  if (!id || id === '—') return '—';
  if (id.length <= 12) return id;
  return `${id.slice(0, 8)}…${id.slice(-4)}`;
}

export default DashboardPanel;
