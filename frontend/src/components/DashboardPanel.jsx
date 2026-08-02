import { Bike, Package } from 'lucide-react';

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
}) => {
  const statusMeta = STATUS_TR[order.status] || {
    label: order.status,
    tone: 'slate',
  };

  return (
    <div className="h-full flex flex-col gap-5 overflow-y-auto pr-1 custom-scrollbar text-slate-800">
      <header className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-[1.35rem] font-semibold tracking-tight text-slate-900">
            Siparişin
          </h2>
          <p className="text-sm text-slate-500 mt-0.5">
            {lastUpdateLabel ? `Güncellendi ${lastUpdateLabel}` : 'Konum bekleniyor'}
          </p>
        </div>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold ring-1 ${
            wsConnected
              ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
              : 'bg-slate-100 text-slate-500 ring-slate-200'
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

      <div
        className={`inline-flex self-start items-center rounded-full px-3 py-1 text-xs font-semibold ring-1 ${
          TONE[statusMeta.tone]
        }`}
      >
        {statusMeta.label}
      </div>

      <section className="rounded-2xl bg-white border border-slate-200/90 shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-100 flex items-center gap-2">
          <Package size={15} className="text-slate-400" />
          <span className="text-sm font-semibold text-slate-800">Teslimat</span>
          <span className="ml-auto font-mono text-[11px] text-slate-400">
            {shortId(order.orderNo)}
          </span>
        </div>

        <ol className="px-4 py-4 space-y-0">
          <Stop
            color="bg-amber-500"
            title="Alış"
            subtitle={order.pickupAddress}
            connector
          />
          <Stop
            color="bg-emerald-500"
            title="Teslimat"
            subtitle={order.deliveryAddress}
          />
        </ol>

        <div className="px-4 py-3 bg-slate-50 border-t border-slate-100 text-sm text-slate-600">
          <span className="text-slate-400">Müşteri</span>
          <span className="float-right font-medium text-slate-800">{order.customerName}</span>
        </div>
      </section>

      <section className="rounded-2xl bg-white border border-slate-200/90 shadow-sm p-4">
        <div className="flex items-center gap-3">
          <div className="h-11 w-11 rounded-full bg-slate-900 text-white flex items-center justify-center shadow-sm">
            <Bike size={20} />
          </div>
          <div className="min-w-0 flex-1">
            <p className="font-semibold text-slate-900 truncate">{courier.name}</p>
            <p className="text-sm text-slate-500">Motosiklet ile geliyor</p>
          </div>
        </div>
      </section>

      <section className="rounded-2xl bg-white border border-slate-200/90 shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b border-slate-100">
          <h3 className="text-sm font-semibold text-slate-800">Sipariş geçmişi</h3>
          <p className="text-xs text-slate-400 mt-0.5">Takip etmek için bir sipariş seç</p>
        </div>
        {orders.length === 0 ? (
          <p className="px-4 py-6 text-sm text-slate-400">Henüz sipariş yok</p>
        ) : (
          <ul className="divide-y divide-slate-100 max-h-52 overflow-y-auto">
            {orders.map((o) => {
              const active = o.trackingNumber === selectedTracking;
              const meta = STATUS_TR[o.status] || { label: o.status, tone: 'slate' };
              return (
                <li key={o.trackingNumber}>
                  <button
                    type="button"
                    onClick={() => onSelectOrder?.(o.trackingNumber)}
                    className={`w-full text-left px-4 py-3 transition-colors ${
                      active ? 'bg-sky-50' : 'hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-[11px] text-slate-500">
                        {shortId(o.trackingNumber)}
                      </span>
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ring-1 ${
                          TONE[meta.tone]
                        }`}
                      >
                        {meta.label}
                      </span>
                    </div>
                    <p className="text-sm text-slate-800 mt-1 truncate">
                      {o.pickupAddress} → {o.deliveryAddress}
                    </p>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
};

function Stop({ color, title, subtitle, connector }) {
  return (
    <li className="relative flex gap-3 pb-5 last:pb-0">
      {connector && (
        <span className="absolute left-[7px] top-4 bottom-0 w-px bg-slate-200" aria-hidden />
      )}
      <span
        className={`relative z-[1] mt-1.5 h-3.5 w-3.5 rounded-full ${color} ring-4 ring-white shrink-0`}
      />
      <div className="min-w-0 pt-0.5">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">{title}</p>
        <p className="text-sm font-medium text-slate-800 leading-snug mt-0.5">{subtitle}</p>
      </div>
    </li>
  );
}

function shortId(id) {
  if (!id || id === '—') return '—';
  if (id.length <= 12) return id;
  return `${id.slice(0, 8)}…${id.slice(-4)}`;
}

export default DashboardPanel;
