function ScreenSplash() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{
        display:"flex", flexDirection:"column",
        alignItems:"center", justifyContent:"center",
        height:"100%", gap:0,
        background: T.bg,
        position:"relative", overflow:"hidden",
      }}>
        {/* subtle scanline texture */}
        <div style={{
          position:"absolute", inset:0, pointerEvents:"none",
          backgroundImage:`repeating-linear-gradient(0deg, transparent, transparent 3px, ${T.border}22 3px, ${T.border}22 4px)`,
          opacity:0.4,
        }}/>
        {/* glow behind icon */}
        <div style={{
          position:"absolute",
          width:180, height:180,
          borderRadius:"50%",
          background:`radial-gradient(circle, ${T.accentDim} 0%, transparent 70%)`,
          pointerEvents:"none",
        }}/>
        {/* icon */}
        <div style={{
          width:64, height:64, borderRadius:14,
          background:T.accentDim, border:`1px solid ${T.accentMid}`,
          display:"flex", alignItems:"center", justifyContent:"center",
          marginBottom:20,
          boxShadow:`0 0 40px ${T.accentMid}`,
          position:"relative",
        }}>
          <svg width="32" height="32" viewBox="0 0 26 26" fill="none">
            <rect x="3" y="11" width="20" height="13" rx="2" stroke={T.accent} strokeWidth="1.5"/>
            <path d="M8 11V8a5 5 0 0 1 10 0v3" stroke={T.accent} strokeWidth="1.5" strokeLinecap="round"/>
            <circle cx="13" cy="17" r="2" fill={T.accent}/>
            <line x1="13" y1="19" x2="13" y2="21" stroke={T.accent} strokeWidth="1.5" strokeLinecap="round"/>
          </svg>
        </div>
        {/* wordmark */}
        <div style={{
          color:T.accent, fontSize:26, fontWeight:700,
          letterSpacing:"-0.02em", lineHeight:1, marginBottom:6,
          position:"relative",
        }}>
          pass<span style={{color:T.textDim, fontWeight:300}}>.android</span>
        </div>
        <div style={{ color:T.textFaint, fontSize:10, letterSpacing:"0.15em", textTransform:"uppercase", position:"relative" }}>
          the unix password manager
        </div>

        {/* bottom loading indicator */}
        <div style={{
          position:"absolute", bottom:52,
          display:"flex", flexDirection:"column", alignItems:"center", gap:10,
        }}>
          <div style={{
            width:120, height:2, background:T.border2, borderRadius:2, overflow:"hidden",
          }}>
            <div style={{
              width:"60%", height:"100%",
              background:`linear-gradient(90deg, ${T.accentMid}, ${T.accent})`,
              borderRadius:2,
              boxShadow:`0 0 6px ${T.accent}`,
              animation:"loading-bar 1.8s ease-in-out infinite",
            }}/>
          </div>
          <div style={{ color:T.textFaint, fontSize:9 }}>initialising…</div>
        </div>
      </div>
    </Phone>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// 01 · WELCOME
// ─────────────────────────────────────────────────────────────────────────────
window.ScreenSplash = ScreenSplash;
