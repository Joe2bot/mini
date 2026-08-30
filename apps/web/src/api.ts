export type Agent = { id: string; name: string; description: string; systemPrompt: string; modelProvider: string; modelName: string; temperature?: number; maxTokens?: number };
export type Tool = { id: string; name: string; description: string; inputSchema: string; sourceType: 'native' | 'mcp'; sourceId: string; enabled: boolean };
export type RunStart = { run_id: string; trace_id: string; status: string };
const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
async function request<T>(path: string, init?: RequestInit): Promise<T> { const response = await fetch(`${base}${path}`, { headers: { 'Content-Type': 'application/json' }, ...init }); if (!response.ok) throw new Error((await response.json()).message ?? 'Request failed'); return response.status === 204 ? undefined as T : response.json(); }
export const api = {
  listAgents: () => request<Agent[]>('/api/agents'),
  createAgent: (agent: Omit<Agent, 'id'>) => request<Agent>('/api/agents', { method: 'POST', body: JSON.stringify(agent) }),
  updateAgent: (id: string, agent: Omit<Agent, 'id'>) => request<Agent>(`/api/agents/${id}`, { method: 'PUT', body: JSON.stringify(agent) }),
  deleteAgent: (id: string) => request<void>(`/api/agents/${id}`, { method: 'DELETE' }),
  listTools: () => request<Tool[]>('/api/tools'),
  agentTools: (id: string) => request<Tool[]>(`/api/agents/${id}/tools`),
  replaceAgentTools: (id: string, toolIds: string[]) => request<Tool[]>(`/api/agents/${id}/tools`, { method: 'PUT', body: JSON.stringify({ toolIds }) }),
  startRun: (id: string, input: string) => request<RunStart>(`/api/agents/${id}/runs`, { method: 'POST', body: JSON.stringify({ input }) }),
  eventsUrl: (runId: string) => `${base}/api/runs/${runId}/events`,
  trace: (traceId: string) => request<any>(`/api/traces/${traceId}`)
};
