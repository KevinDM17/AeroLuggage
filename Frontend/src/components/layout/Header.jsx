import { Bell, Search, User } from "lucide-react";

export default function Header() {
  return (
    <header className="h-20 bg-white border-b border-slate-200 flex items-center justify-between px-8 z-10 w-full relative">
      <div className="flex items-center bg-slate-100 px-4 py-2.5 rounded-full w-96 transition-all focus-within:ring-2 focus-within:ring-blue-500/20 focus-within:bg-white border text-sm border-transparent focus-within:border-blue-400">
        <Search className="w-4 h-4 text-slate-400 mr-2" />
        <input 
          type="text" 
          placeholder="Buscar vuelos, aeropuertos, IDs de maletas..." 
          className="bg-transparent border-none outline-none w-full text-slate-700 placeholder:text-slate-400"
        />
      </div>

      <div className="flex items-center gap-5">
        <button className="relative p-2 rounded-full hover:bg-slate-100 text-slate-500 transition-colors">
          <Bell className="w-5 h-5" />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full border-2 border-white"></span>
        </button>
      </div>
    </header>
  );
}
