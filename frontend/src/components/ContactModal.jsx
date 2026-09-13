import { useEffect, useState } from 'react';
import { Bike, MessageSquare, Phone, ShieldCheck, X } from 'lucide-react';

const ContactModal = ({ courier, onClose }) => {
  const [notice, setNotice] = useState('');

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose();
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const showUnavailableNotice = (channel) => {
    setNotice(`${channel} özelliği şu anda kullanılamıyor.`);
  };

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-slate-900/20 p-4 backdrop-blur-[2px]"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="contact-modal-title"
        className="w-full max-w-md rounded-xl border border-slate-200 bg-white p-5 shadow-[0_24px_70px_rgba(15,23,42,0.18)]"
      >
        <header className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-blue-600">
              Teslimat desteği
            </p>
            <h2 id="contact-modal-title" className="mt-1 text-xl font-semibold text-slate-950">
              Kurye ile iletişim
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            autoFocus
            aria-label="İletişim penceresini kapat"
            className="rounded-lg border border-slate-200 p-2 text-slate-500 transition hover:bg-slate-50 hover:text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <X size={18} />
          </button>
        </header>

        <div className="mt-5 flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <div className="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-white text-blue-700 ring-1 ring-slate-200">
            <Bike size={21} />
          </div>
          <div className="min-w-0">
            <p className="truncate font-semibold text-slate-950">{courier.name}</p>
            <p className="mt-0.5 text-sm text-slate-500">
              Motosiklet <span aria-hidden>•</span> {courier.plate || 'Plaka bilgisi yok'}
            </p>
          </div>
        </div>

        <div className="mt-4">
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Güvenli numara</p>
          <p className="mt-1 font-mono text-base font-semibold text-slate-900">
            {courier.phone || 'Numara bilgisi yok'}
          </p>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-3">
          <button
            type="button"
            onClick={() => showUnavailableNotice('Arama')}
            className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          >
            <Phone size={17} />
            Ara
          </button>
          <button
            type="button"
            onClick={() => showUnavailableNotice('Mesaj gönderme')}
            className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
          >
            <MessageSquare size={17} />
            Mesaj gönder
          </button>
        </div>

        {notice && (
          <p role="status" className="mt-4 rounded-lg bg-blue-50 px-3 py-2.5 text-sm text-blue-800">
            {notice}
          </p>
        )}

        <p className="mt-4 flex items-start gap-2 text-xs leading-relaxed text-slate-500">
          <ShieldCheck size={15} className="mt-0.5 shrink-0 text-emerald-600" />
          Kişisel verileri korumak için telefon numarası maskelenmiştir.
        </p>
      </section>
    </div>
  );
};

export default ContactModal;
