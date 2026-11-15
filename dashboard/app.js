'use strict';

// The services own the records. These lists only remember this page's demo.
const state = { animals: [], trays: [], products: [] };
const $ = selector => document.querySelector(selector);
const forms = {
  animal: $('#animal-form'), tray: $('#tray-form'), part: $('#part-form'), product: $('#product-form')
};
const endpoints = { registration: 'http://localhost:8081', processing: 'http://localhost:8082', packaging: 'http://localhost:8083' };
let pending = 0;

function unique(prefix) {
  return `${prefix}-${crypto.randomUUID().slice(0, 8).toUpperCase()}`;
}
function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
}
function setDefaults() {
  forms.animal.elements.earTag.value = unique('DEMO');
  forms.animal.elements.arrivalDate.value = today();
  forms.animal.elements.weight.value = '525';
  forms.animal.elements.origin.value = 'Example Farm';
  forms.tray.elements.rfid.value = unique('TRAY');
  forms.tray.elements.maxWeight.value = '10';
  forms.part.elements.partType.value = 'leg';
  forms.part.elements.weight.value = '2.5';
  forms.product.elements.productType.value = 'Packaged cuts';
}
function message(form, text, error = false) {
  const el = form.querySelector('.feedback');
  el.textContent = text;
  el.classList.toggle('error', error);
}
function element(tag, text, className) {
  const el = document.createElement(tag);
  if (text !== undefined) el.textContent = text;
  if (className) el.className = className;
  return el;
}
function empty(container, text) { container.replaceChildren(element('p', text, 'empty')); }
function record(title, details, id, lines = []) {
  const row = element('div', undefined, 'record');
  row.append(element('strong', title), element('p', details));
  if (id) row.append(element('p', id, 'record-id'));
  if (lines.length) {
    const list = element('ul');
    for (const line of lines) list.append(element('li', line));
    row.append(list);
  }
  return row;
}
function renderSelect(select, items, placeholder, valueOf, textOf) {
  const selected = select.value;
  select.replaceChildren(new Option(placeholder, ''));
  for (const item of items) select.add(new Option(textOf(item), valueOf(item)));
  if (items.some(item => valueOf(item) === selected)) select.value = selected;
  else if (items.length) select.value = valueOf(items[items.length - 1]);
}
function render() {
  $('#animal-count').textContent = `${state.animals.length} ${state.animals.length === 1 ? 'animal' : 'animals'}`;
  $('#tray-count').textContent = `${state.trays.length} ${state.trays.length === 1 ? 'tray' : 'trays'}`;
  $('#product-count').textContent = `${state.products.length} ${state.products.length === 1 ? 'product' : 'products'}`;
  $('#animal-badge').textContent = state.animals.length;
  $('#tray-badge').textContent = state.trays.length;
  $('#product-badge').textContent = state.products.length;

  const animals = $('#animals'); animals.replaceChildren();
  if (!state.animals.length) empty(animals, 'No animals registered yet.');
  for (const a of state.animals) animals.append(record(a.earTag, `${a.origin} · ${a.weight} kg · ${a.arrivalDate}`, `ID ${a.id}`));
  const trays = $('#trays'); trays.replaceChildren();
  if (!state.trays.length) empty(trays, 'No trays created yet.');
  for (const tray of state.trays) {
    const parts = tray.parts.map(part => {
      const animal = state.animals.find(a => a.id === part.animalId);
      return `${part.partType} · ${part.weight} kg · ${animal ? animal.earTag : part.animalId}`;
    });
    trays.append(record(tray.rfid, `${Number(tray.currentWeight).toFixed(2)} / ${tray.maxWeight} kg · ${tray.partType || 'empty'}`, null, parts));
  }
  const products = $('#products'); products.replaceChildren();
  if (!state.products.length) empty(products, 'No products packaged yet.');
  for (const p of state.products) products.append(record(p.productType, `Trays: ${p.trayRfids.join(', ')}`, `ID ${p.id}`));
  renderSelect(forms.part.elements.animalId, state.animals, 'Select an animal', a => a.id, a => a.earTag);
  renderSelect(forms.part.elements.trayRfid, state.trays, 'Select a tray', t => t.rfid, t => t.rfid);

  const choices = $('#tray-choices');
  const selected = new Set(Array.from(choices.querySelectorAll('input:checked'), input => input.value));
  choices.replaceChildren();
  if (!state.trays.length) empty(choices, 'Create a tray in processing first.');
  for (const tray of state.trays) {
    const label = element('label');
    const input = document.createElement('input'); input.type = 'checkbox'; input.value = tray.rfid; input.checked = selected.has(tray.rfid);
    label.append(input, document.createTextNode(`${tray.rfid} · ${tray.partType || 'empty'}`)); choices.append(label);
  }
}
async function api(base, path, body, context) {
  let response;
  try {
    response = await fetch(base + path, body === undefined ? {} : {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    });
  } catch (_) {
    throw new Error(`${context} is unavailable. Check that the service is running and try again.`);
  }
  if (!response.ok) {
    if (response.status === 409) throw new Error(context === 'Registration' ? 'That ear tag is already registered. Try another.' : context === 'Tray' ? 'That tray RFID already exists. Try another.' : 'The part conflicts with the tray: check its type and remaining capacity.');
    if (response.status === 400) throw new Error('Please check the required fields and use positive weights.');
    if (response.status === 404) throw new Error('The selected record was not found.');
    throw new Error(`${context} could not complete the request (HTTP ${response.status}).`);
  }
  return response.json();
}
function submitting(form, action) {
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true; pending++; $('#reset').disabled = true; message(form, '');
    try { await action(); }
    catch (error) { message(form, error.message || 'Something went wrong. Try again.', true); }
    finally { button.disabled = false; pending--; $('#reset').disabled = pending > 0; }
  });
}
submitting(forms.animal, async () => {
  const f = forms.animal.elements;
  const data = { earTag: f.earTag.value.trim(), arrivalDate: f.arrivalDate.value, weight: Number(f.weight.value), origin: f.origin.value.trim() };
  if (!data.earTag || !data.origin) throw new Error('Enter a nonblank ear tag and origin.');
  const animal = await api(endpoints.registration, '/registrations', data, 'Registration');
  state.animals.push(animal); render(); f.earTag.value = unique('DEMO');
  message(forms.animal, `${animal.earTag} registered. Its event will be sent asynchronously.`);
});
submitting(forms.tray, async () => {
  const f = forms.tray.elements;
  const data = { rfid: f.rfid.value.trim(), maxWeight: Number(f.maxWeight.value) };
  if (!data.rfid) throw new Error('Enter a nonblank tray RFID.');
  const tray = await api(endpoints.processing, '/trays', data, 'Tray');
  state.trays.push(tray); render(); f.rfid.value = unique('TRAY');
  message(forms.tray, `${tray.rfid} created.`);
});
submitting(forms.part, async () => {
  const f = forms.part.elements;
  const body = { animalId: f.animalId.value, partType: f.partType.value.trim(), weight: Number(f.weight.value), trayRfid: f.trayRfid.value };
  if (!body.animalId || !body.trayRfid || !body.partType) throw new Error('Select an animal and tray, and enter a part type.');
  const part = await api(endpoints.processing, '/parts', body, 'Processing');
  const tray = state.trays.find(t => t.rfid === body.trayRfid);
  // The part is already saved. Never retry its POST if refreshing the tray fails.
  tray.parts.push(part); tray.partType = part.partType; tray.currentWeight = Number(tray.currentWeight) + Number(part.weight);
  try {
    const updated = await api(endpoints.processing, `/trays/${encodeURIComponent(body.trayRfid)}`, undefined, 'Processing');
    Object.assign(tray, updated);
    message(forms.part, `Part added to ${tray.rfid}. Its event will be sent asynchronously.`);
  } catch (_) { message(forms.part, 'Part saved, but the tray could not be refreshed. Displayed weight is based on this session.', true); }
  render();
});
submitting(forms.product, async () => {
  const type = forms.product.elements.productType.value.trim();
  const trayRfids = Array.from($('#tray-choices').querySelectorAll('input:checked'), input => input.value);
  if (!type || !trayRfids.length) throw new Error('Enter a product type and select at least one tray.');
  const product = await api(endpoints.packaging, '/products', { productType: type, trayRfids }, 'Packaging');
  state.products.push(product); render();
  message(forms.product, 'Product created. Its event will be sent asynchronously.');
});
$('#reset').addEventListener('click', () => {
  if (pending) return;
  state.animals.length = 0; state.trays.length = 0; state.products.length = 0;
  for (const form of Object.values(forms)) { form.reset(); message(form, ''); }
  $('#tray-choices').replaceChildren(); setDefaults(); render();
});
setDefaults(); render();

// Switching views hides panels rather than rebuilding them, preserving session state.
const tabs = [$('#station-tab'), $('#trace-tab')];
function activateTab(index, focus = false) {
  tabs.forEach((tab, i) => {
    const active = i === index;
    tab.setAttribute('aria-selected', String(active)); tab.tabIndex = active ? 0 : -1;
    document.getElementById(tab.getAttribute('aria-controls')).hidden = !active;
    if (active && focus) tab.focus();
  });
}
tabs.forEach((tab, index) => {
  tab.addEventListener('click', () => activateTab(index));
  tab.addEventListener('keydown', event => {
    if (['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) {
      event.preventDefault();
      activateTab(event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + 1) % tabs.length, true);
    }
  });
});
const animalSearch = $('#animal-search'), productSearch = $('#product-search');
function updateTraceOptions() {
  for (const [select, items, valueOf, labelOf, placeholder] of [
    [animalSearch.elements.animalSelection, state.animals, a => a.earTag, a => a.earTag, 'Enter an ear tag instead'],
    [productSearch.elements.productSelection, state.products, p => p.id, p => `${p.productType} · ${p.id}`, 'Enter a product ID instead']
  ]) {
    const selected = select.value;
    select.replaceChildren(new Option(placeholder, ''));
    for (const item of items) select.add(new Option(labelOf(item), valueOf(item)));
    select.value = items.some(item => valueOf(item) === selected) ? selected : '';
  }
}
animalSearch.elements.animalSelection.addEventListener('change', event => { if (event.target.value) animalSearch.elements.earTag.value = event.target.value; });
productSearch.elements.productSelection.addEventListener('change', event => { if (event.target.value) productSearch.elements.productId.value = event.target.value; });
for (const [input, select] of [[animalSearch.elements.earTag, animalSearch.elements.animalSelection], [productSearch.elements.productId, productSearch.elements.productSelection]]) {
  input.addEventListener('input', () => { if (input.value !== select.value) select.value = ''; });
}
async function search(path) {
  let response;
  try { response = await fetch(`http://localhost:8084${path}`); }
  catch (_) { throw new Error('Traceability is unavailable. Start TraceabilityService and try again.'); }
  if (response.status === 400) throw new Error('Enter a valid identifier.');
  if (response.status === 404) throw new Error('Record not found. If it was just created, wait for its event and try again.');
  if (!response.ok) throw new Error('Traceability could not complete the search. Try again.');
  return response.json();
}
submitting(animalSearch, async () => {
  const value = animalSearch.elements.earTag.value.trim();
  const container = $('#animal-results');
  if (!value) { empty(container, 'No results to display.'); throw new Error('Enter an animal ear tag.'); }
  empty(container, 'Searching…');
  let result;
  try { result = await search(`/traceability/products?earTag=${encodeURIComponent(value)}`); }
  catch (error) { empty(container, 'No results to display.'); throw error; }
  container.replaceChildren();
  if (!result.products.length) empty(container, 'No linked products found. Recent events may still be in transit.');
  for (const product of result.products) container.append(record(product.productType, '', `ID ${product.id}`));
  message(animalSearch, `Found ${result.products.length} ${result.products.length === 1 ? 'product' : 'products'}.`);
});
submitting(productSearch, async () => {
  const value = productSearch.elements.productId.value.trim();
  const container = $('#product-results');
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) { empty(container, 'No results to display.'); throw new Error('Enter a valid product UUID.'); }
  empty(container, 'Searching…');
  let result;
  try { result = await search(`/traceability/animals?productId=${encodeURIComponent(value)}`); }
  catch (error) { empty(container, 'No results to display.'); throw error; }
  container.replaceChildren();
  if (!result.animals.length) empty(container, 'No linked animals found. Recent events may still be in transit.');
  for (const earTag of result.animals) container.append(record(earTag, '', null));
  message(productSearch, `Found ${result.animals.length} ${result.animals.length === 1 ? 'animal' : 'animals'}.`);
});
// Existing station renders update selection lists; input values and results remain untouched.
const originalRender = render;
render = function () { originalRender(); updateTraceOptions(); };
$('#reset').addEventListener('click', () => {
  animalSearch.reset(); productSearch.reset();
  message(animalSearch, ''); message(productSearch, '');
  empty($('#animal-results'), 'Search for an animal to see linked products.');
  empty($('#product-results'), 'Search for a product to see linked animals.');
  updateTraceOptions();
});
updateTraceOptions();
