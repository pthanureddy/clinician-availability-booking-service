export async function request(path, body) {
  const response = await fetch(`/api${path}`, body === undefined ? {} : {
    method:'POST', headers:{'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()}, body:JSON.stringify(body)
  });
  const value = await response.json();
  if (!response.ok) throw new Error(value.detail || 'Scheduling request failed');
  return value;
}
