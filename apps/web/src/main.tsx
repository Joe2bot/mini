import { FormEvent, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Agent, api } from './api';
import './styles.css';

type AgentForm = Omit<Agent, 'id'>;
const blank: AgentForm = { name: '', description: '', systemPrompt: 'You are a helpful assistant.', modelProvider: 'fake', modelName: 'fake', temperature: 0.2, maxTokens: 512 };

function App() {
  const [agents, setAgents] = useState<Agent[]>([]); const [form, setForm] = useState<AgentForm>(blank); const [editing, setEditing] = useState<string | null>(null);
  const [selected, setSelected] = useState<string>(''); const [message, setMessage] = useState(''); const [status, setStatus] = useState('idle'); const [output, setOutput] = useState(''); const [trace, setTrace] = useState<any>(null); const [error, setError] = useState('');
  const refresh = () => api.listAgents().then(items => { setAgents(items); if (!selected && items[0]) setSelected(items[0].id); }).catch(e => setError(e.message));
  useEffect(() => { void refresh(); }, []);
  async function save(e: FormEvent) { e.preventDefault(); setError(''); try { if (editing) await api.updateAgent(editing, form); else await api.createAgent(form); setForm(blank); setEditing(null); refresh(); } catch (err) { setError((err as Error).message); } }
  async function run() { if (!selected || !message.trim()) return; setOutput(''); setTrace(null); setError(''); try { const run = await api.startRun(selected, message); setStatus(run.status); const source = new EventSource(api.eventsUrl(run.run_id)); source.addEventListener('run.started', () => setStatus('running'));
    source.addEventListener('llm.delta', event => { const envelope = JSON.parse((event as MessageEvent).data); setOutput(value => value + envelope.data.delta); });
    source.addEventListener('run.completed', async event => { const envelope = JSON.parse((event as MessageEvent).data); setStatus('completed'); setOutput(envelope.data.output); source.close(); setTrace(await api.trace(run.trace_id)); });
    source.addEventListener('run.failed', event => { const envelope = JSON.parse((event as MessageEvent).data); setStatus('failed'); setError(envelope.data.message); source.close(); });
  } catch (err) { setError((err as Error).message); setStatus('failed'); } }
  function edit(agent: Agent) { setEditing(agent.id); setForm({ name: agent.name, description: agent.description, systemPrompt: agent.systemPrompt, modelProvider: agent.modelProvider, modelName: agent.modelName, temperature: agent.temperature, maxTokens: agent.maxTokens }); }
  return <main><header><h1>Mini Coze</h1><p>Agent Run vertical slice</p></header>{error && <p className="error">{error}</p>}<section className="grid"><div className="card"><h2>Agents</h2><form onSubmit={save}><input required placeholder="Name" value={form.name} onChange={e => setForm({...form,name:e.target.value})}/><input required placeholder="Description" value={form.description} onChange={e => setForm({...form,description:e.target.value})}/><textarea required placeholder="System prompt" value={form.systemPrompt} onChange={e => setForm({...form,systemPrompt:e.target.value})}/><input required placeholder="Provider" value={form.modelProvider} onChange={e => setForm({...form,modelProvider:e.target.value})}/><input required placeholder="Model" value={form.modelName} onChange={e => setForm({...form,modelName:e.target.value})}/><button>{editing ? 'Update agent' : 'Create agent'}</button>{editing && <button type="button" onClick={() => {setEditing(null);setForm(blank)}}>Cancel</button>}</form><ul>{agents.map(agent => <li key={agent.id}><button className={selected===agent.id?'selected':''} onClick={()=>setSelected(agent.id)}>{agent.name}</button><button onClick={()=>edit(agent)}>Edit</button><button onClick={async()=>{await api.deleteAgent(agent.id);refresh()}}>Delete</button></li>)}</ul></div><div className="card"><h2>Run Agent</h2><p>Status: <strong>{status}</strong></p><textarea placeholder="User message" value={message} onChange={e=>setMessage(e.target.value)}/><button disabled={!selected || !message.trim() || status==='queued' || status==='running'} onClick={run}>Run</button><h3>Streaming output</h3><pre>{output || 'Waiting for a run…'}</pre><h3>Trace</h3><pre>{trace ? JSON.stringify(trace, null, 2) : 'Trace will appear after completion.'}</pre></div></section></main>;
}
createRoot(document.getElementById('root')!).render(<App />);
