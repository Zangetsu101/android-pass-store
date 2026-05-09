// ── Theme tokens ─────────────────────────────────────────────────────────────
const DARK_T = {
  bg:       "#0b0d0b",
  surface:  "#0f120f",
  raised:   "#131a13",
  border:   "#1d2b1d",
  border2:  "#243324",
  accent:   "#39ff6b",
  accentDim:"#39ff6b20",
  accentMid:"#39ff6b44",
  text:     "#c8e6c9",
  textDim:  "#527a52",
  textFaint:"#2e4a2e",
  danger:   "#ff5555",
  warn:     "#ffcc44",
  mono:     "'JetBrains Mono', monospace",
  r:        4,
};

const LIGHT_T = {
  bg:       "#f5f2eb",
  surface:  "#ede9e0",
  raised:   "#e8e4db",
  border:   "#d4cfc4",
  border2:  "#c8c2b5",
  accent:   "#1a6b3c",
  accentDim:"#1a6b3c18",
  accentMid:"#1a6b3c35",
  text:     "#1c1a16",
  textDim:  "#7a7265",
  textFaint:"#b0a898",
  danger:   "#c0392b",
  warn:     "#b7791f",
  mono:     "'JetBrains Mono', monospace",
  r:        4,
};

const ThemeContext = React.createContext(DARK_T);

// ── Phone shell ──────────────────────────────────────────────────────────────
function Phone({ children, footer }) {
  const T = React.useContext(ThemeContext);
  return (
    <div style={{
      width: 320, height: 680,
      borderRadius: 38,
      background: "#161616",
      boxShadow: "0 0 0 1.5px #2a2a2a, 0 0 0 3px #111, 0 40px 100px rgba(0,0,0,0.8)",
      display: "flex", flexDirection: "column",
      overflow: "hidden", position: "relative",
      fontFamily: T.mono,
    }}>
      <div style={{ position:"absolute", top:12, left:"50%", transform:"translateX(-50%)", width:10, height:10, borderRadius:"50%", background:"#0a0a0a", zIndex:30 }} />
      <div style={{
        height:36, background:T.bg, flexShrink:0, zIndex:10,
        display:"flex", alignItems:"flex-end", paddingBottom:5, paddingInline:18,
        justifyContent:"space-between",
      }}>
        <span style={{color:T.textDim, fontSize:10}}>9:41</span>
        <div style={{display:"flex", gap:4, alignItems:"center"}}>
          {[3,5,7].map(h=><div key={h} style={{width:3,height:h,background:T.textDim,borderRadius:1}}/>)}
          <svg width="11" height="9" viewBox="0 0 11 9" fill="none">
            <path d="M5.5 7.5a.75.75 0 1 0 0 1.5.75.75 0 0 0 0-1.5z" fill={T.textDim}/>
            <path d="M2 4.5C3 3.4 4.2 3 5.5 3s2.5.4 3.5 1.5" stroke={T.textDim} strokeWidth="1.1" strokeLinecap="round" fill="none"/>
            <path d="M0 2C1.6.5 3.4 0 5.5 0s3.9.5 5.5 2" stroke={T.textDim} strokeWidth="1.1" strokeLinecap="round" fill="none" opacity=".4"/>
          </svg>
          <div style={{display:"flex",alignItems:"center",gap:1}}>
            <div style={{width:18,height:9,border:`1.2px solid ${T.textDim}`,borderRadius:2,padding:1.5}}>
              <div style={{width:"65%",height:"100%",background:T.textDim,borderRadius:1}}/>
            </div>
          </div>
        </div>
      </div>
      <div style={{flex:1, background:T.bg, overflowY:"auto", overflowX:"hidden", position:"relative"}}>
        {children}
      </div>
      {footer || (
        <div style={{height:28, background:T.bg, flexShrink:0, display:"flex", justifyContent:"center", alignItems:"center", gap:24, borderTop:`1px solid ${T.border}`}}>
          <div style={{width:16,height:16,border:`1.2px solid ${T.textFaint}`,borderRadius:3}}/>
          <div style={{width:28,height:3,background:T.textFaint,borderRadius:4}}/>
          <div style={{width:12,height:12,borderRadius:"50%",border:`1.2px solid ${T.textFaint}`}}/>
        </div>
      )}
    </div>
  );
}

// ── Primitives ───────────────────────────────────────────────────────────────
const Btn = ({children, full, dim, danger, style:s={}}) => {
  const T = React.useContext(ThemeContext);
  return (
  <div style={{
    background: danger ? "#ff555520" : dim ? "transparent" : T.accentDim,
    border: `1px solid ${danger ? T.danger : dim ? T.border2 : T.accent}`,
    color: danger ? T.danger : dim ? T.textDim : T.accent,
    borderRadius: T.r,
    padding: "10px 16px",
    fontSize: 11, fontFamily: T.mono, fontWeight: 600,
    textAlign: "center", cursor: "pointer",
    letterSpacing: "0.05em",
    width: full ? "100%" : "auto",
    boxShadow: (!dim && !danger) ? `0 0 12px ${T.accentDim}` : "none",
    ...s,
  }}>{children}</div>
  );
};

const Label = ({children, dim}) => {
  const T = React.useContext(ThemeContext);
  return (<div style={{color: dim ? T.textDim : T.accent, fontSize:9, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:6, fontWeight:600}}>
    {children}
  </div>);
};

const FAB = () => {
  const T = React.useContext(ThemeContext);
  return (
  <div style={{position:"absolute", bottom:40, right:16}}>
    <div style={{
      width:44, height:44, borderRadius:T.r,
      background:T.accentDim, border:`1px solid ${T.accent}`,
      display:"flex", alignItems:"center", justifyContent:"center",
      color:T.accent, fontSize:22, fontWeight:300,
      boxShadow:`0 0 20px ${T.accentMid}`,
    }}>+</div>
  </div>
  );
};

function Shimmer({ width="100%", height=14, radius=3, style:s={} }) {
  const T = React.useContext(ThemeContext);
  return (
    <div style={{
      width, height, borderRadius: radius,
      background: `linear-gradient(90deg, ${T.border} 0%, ${T.border2} 40%, ${T.border} 100%)`,
      backgroundSize:"200% 100%",
      animation:"shimmer 1.4s ease-in-out infinite",
      ...s,
    }}/>
  );
}

// ── Data ─────────────────────────────────────────────────────────────────────
const storeData = [
  { folder:"email",   entries:["gmail","proton","hey"] },
  { folder:"dev",     entries:["github","npm","pypi"] },
  { folder:"finance", entries:["revolut","wise"] },
  { folder:"social",  entries:["twitter","mastodon"] },
];

const flatEntries = [
  {name:"github",   path:"dev/github",   time:"3d"},
  {name:"gmail",    path:"email/gmail",  time:"2h"},
  {name:"hey",      path:"email/hey",    time:"1w"},
  {name:"mastodon", path:"social/mastodon",time:"2w"},
  {name:"npm",      path:"dev/npm",      time:"5d"},
  {name:"proton",   path:"email/proton", time:"1d"},
  {name:"pypi",     path:"dev/pypi",     time:"1mo"},
  {name:"revolut",  path:"finance/revolut",time:"2w"},
  {name:"twitter",  path:"social/twitter",time:"3w"},
  {name:"wise",     path:"finance/wise", time:"1mo"},
];

// Expose to other Babel scripts
Object.assign(window, {
  DARK_T, LIGHT_T, ThemeContext,
  Phone, Btn, Label, FAB, Shimmer,
  storeData, flatEntries,
});
