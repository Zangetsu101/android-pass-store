function ScreenHomeSyncing() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{ display:"flex", flexDirection:"column", height:"100%" }}>
        {/* topbar */}
        <div style={{
          padding:"12px 18px 10px", borderBottom:`1px solid ${T.border}`,
          display:"flex", alignItems:"center", justifyContent:"space-between", flexShrink:0,
        }}>
          <div>
            <div style={{ color:T.textDim, fontSize:9, letterSpacing:"0.1em" }}>~/.password-store</div>
            <div style={{ color:T.accent, fontSize:14, fontWeight:700, marginTop:1 }}>
              pass<span style={{ color:T.textDim, fontWeight:300 }}>.android</span>
            </div>
          </div>
          <div style={{ display:"flex", gap:10, alignItems:"center" }}>
            {/* sync chip — active/spinning */}
            <div style={{
              display:"flex", alignItems:"center", gap:5,
              background:T.accentDim, border:`1px solid ${T.accent}`,
              borderRadius:T.r, padding:"5px 9px",
            }}>
              <svg width="11" height="11" viewBox="0 0 11 11" fill="none"
                style={{ animation:"spin 0.8s linear infinite", transformOrigin:"center" }}>
                <path d="M9.5 5.5A4 4 0 1 1 7 2" stroke={T.accent} strokeWidth="1.3" strokeLinecap="round"/>
                <path d="M7 2l1 2.5-2.5.5" stroke={T.accent} strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <span style={{ color:T.accent, fontSize:9 }}>syncing</span>
            </div>
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <rect x="0.5" y="0.5" width="5" height="5" rx="1" stroke={T.accent} strokeWidth="1.1"/>
              <rect x="7.5" y="0.5" width="5" height="5" rx="1" stroke={T.textDim} strokeWidth="1.1"/>
              <rect x="0.5" y="7.5" width="5" height="5" rx="1" stroke={T.textDim} strokeWidth="1.1"/>
              <rect x="7.5" y="7.5" width="5" height="5" rx="1" stroke={T.textDim} strokeWidth="1.1"/>
            </svg>
            {/* settings */}
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M5.5 1.5h3l.4 1.4c.3.1.6.3.9.5l1.4-.4 1.5 2.5-1 1c0 .3.1.6.1.9s0 .6-.1.9l1 1-1.5 2.5-1.4-.4c-.3.2-.6.4-.9.5l-.4 1.4h-3l-.4-1.4a4 4 0 0 1-.9-.5l-1.4.4L1.2 9.4l1-.9a4 4 0 0 1-.1-.9c0-.3 0-.6.1-.9l-1-1 1.5-2.5 1.4.4c.3-.2.6-.4.9-.5l.5-1.6z" stroke={T.textDim} strokeWidth="1.1" strokeLinejoin="round"/>
              <circle cx="7" cy="7" r="1.8" stroke={T.textDim} strokeWidth="1.1"/>
            </svg>
          </div>
        </div>

        {/* sync status banner */}
        <div style={{
          margin:"8px 16px 0", padding:"8px 12px",
          background:T.accentDim, border:`1px solid ${T.accentMid}`,
          borderRadius:T.r, display:"flex", alignItems:"center", gap:8,
        }}>
          <div style={{
            width:10, height:10, borderRadius:"50%",
            border:`1.5px solid ${T.border2}`, borderTopColor:T.accent,
            animation:"spin 0.8s linear infinite", flexShrink:0,
          }}/>
          <span style={{ color:T.accent, fontSize:10 }}>git pull --rebase origin main</span>
        </div>

        {/* tree — dimmed while syncing */}
        <div style={{ flex:1, overflowY:"auto", padding:"6px 0", opacity:0.45, pointerEvents:"none" }}>
          {storeData.map(({folder, entries}) => (
            <div key={folder}>
              <div style={{ display:"flex", alignItems:"center", gap:8, padding:"7px 18px" }}>
                <span style={{ color:T.accentMid, fontSize:10 }}>▼</span>
                <span style={{ color:T.accent, fontSize:11, opacity:0.75 }}>{folder}/</span>
                <span style={{ color:T.textFaint, fontSize:9, marginLeft:"auto" }}>{entries.length}</span>
              </div>
              {entries.map((e,i) => (
                <div key={e} style={{
                  display:"flex", alignItems:"center",
                  padding:"8px 18px 8px 40px", borderBottom:`1px solid ${T.border}`,
                }}>
                  <span style={{ color:T.textFaint, fontSize:10, marginRight:8 }}>
                    {i<entries.length-1?"├─":"└─"}
                  </span>
                  <span style={{ color:T.text, fontSize:11, flex:1 }}>{e}</span>
                </div>
              ))}
            </div>
          ))}
        </div>


      </div>
    </Phone>
  );
}
window.ScreenHomeSyncing = ScreenHomeSyncing;
