import React, {useEffect,useState} from 'react';
import {request} from './api.js';
export default function App() {
  const [market,setMarket]=useState('SE'), [language,setLanguage]=useState('en');
  const [patient,setPatient]=useState('synthetic-patient'),[slots,setSlots]=useState([]),[bookings,setBookings]=useState([]);
  const [selected,setSelected]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  async function load() {
    const [s,b]=await Promise.all([request(`/slots?market=${market}&specialty=GP&language=${language}`),request('/bookings')]);
    setSlots(s);setBookings(b);
  }
  useEffect(()=>{let active=true; Promise.all([request(`/slots?market=${market}&specialty=GP&language=${language}`),request('/bookings')]).then(([s,b])=>{if(active){setSlots(s);setBookings(b);setError('');}}).catch(e=>{if(active)setError(e.message);});return()=>{active=false;};},[market,language]);
  async function mutate(path,body) {
    setBusy(true);setError('');
    try {await request(path,body);await load();setSelected('');} catch(e){setError(e.message);} finally{setBusy(false);}
  }
  return <main><p className="eyebrow">Independent portfolio · synthetic data</p><h1>Clinician availability</h1><p>Match care by market and language. Times are displayed in your browser's local time zone.</p>
    <section aria-label="Care filters"><label>Market<select value={market} onChange={e=>{setMarket(e.target.value);setSelected('');}}>{['SE','NO','FR','UK'].map(m=><option key={m}>{m}</option>)}</select></label>
    <label>Language<select value={language} onChange={e=>{setLanguage(e.target.value);setSelected('');}}>{['en','sv','no','fr'].map(l=><option key={l}>{l}</option>)}</select></label>
    <label>Synthetic patient ID<input value={patient} onChange={e=>setPatient(e.target.value)}/></label></section>
    {error && <p role="alert">{error}</p>}<h2>Available GP appointments</h2>
    {slots.length===0?<p>No matching appointments.</p>:slots.map(s=><label className="slot" key={s.id}><input type="radio" name="slot" checked={selected===s.id} onChange={()=>setSelected(s.id)}/>{s.clinician} · {new Date(s.starts).toLocaleString()} · {s.language}</label>)}
    <button disabled={busy||!selected||!patient} onClick={()=>mutate('/bookings',{slot:selected,need:{patient,market,specialty:'GP',language}})}>Book selected appointment</button>
    <h2>Bookings</h2>{bookings.length===0?<p>No bookings yet.</p>:bookings.map(b=><article key={b.id}><strong>{b.patient}</strong><p>{b.market} · {b.slot} · {b.active?'Booked':'Cancelled'}</p>{b.active&&<><button disabled={busy||!selected||b.market!==market||b.language!==language} onClick={()=>mutate(`/bookings/${b.id}/rebook`,{slot:selected})}>Move to selected appointment</button><button disabled={busy} onClick={()=>mutate(`/bookings/${b.id}/cancel`,{})}>Cancel booking</button></>}</article>)}
  </main>;
}
