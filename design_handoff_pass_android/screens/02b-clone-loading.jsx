function ScreenCloneLoading() {
  const T = React.useContext(ThemeContext);
  const lines = [
    { text: "$ git clone git@github.com:user/pass-store.git", color: T.textDim },
    { text: "Cloning into 'pass-store'...", color: T.textDim },
    { text: "remote: Enumerating objects: 84, done.", color: T.text },
    { text: "remote: Counting objects: 100% (84/84), done.", color: T.text },
    { text: "remote: Compressing objects: 100% (61/61), done.", color: T.text },
    { text: "Receiving objects:  73% (62/84)", color: T.accent, blink: true },
  ];
  const pct = 73;
  return (
    <Phone>
      <div style={{ display:"flex", flexDirection:"column", height:"100%", padding:"20px 20px 24px" }}>
        {/* header */}
        <div style={{ marginBottom: 28 }}>
          <div style={{ display:"flex", alignItems:"center", justifyContent:"space-between", marginBottom:10 }}>
            <div style={{ color:T.textDim, fontSize:9, letterSpacing:"0.1em" }}>SETUP · 1 / 2</div>
            <div style={{ display:"flex", gap:4 }}>
              {[1,2].map(i=>(
                <div key={i} style={{ width:20, height:3, borderRadius:4, background:T.accent, opacity:i===1?1:0.3 }}/>
              ))}
            </div>
          </div>
          <div style={{ color: T.accent, fontSize: 16, fontWeight: 700, marginBottom: 4 }}>cloning store</div>
          <div style={{ color: T.textDim, fontSize: 10 }}>github.com:user/pass-store</div>
        </div>

        {/* terminal output */}
        <div style={{
          flex: 1,
          background: T.surface, border: `1px solid ${T.border2}`,
          borderRadius: T.r, padding: "12px 14px",
          overflowY: "auto", marginBottom: 16,
          position: "relative",
        }}>
          {/* scanline shimmer */}
          <div style={{
            position:"absolute", inset:0, pointerEvents:"none",
            background:"linear-gradient(180deg, transparent 0%, #39ff6b06 50%, transparent 100%)",
            backgroundSize:"100% 40px",
            animation:"scanline 3s linear infinite",
            borderRadius: T.r,
          }}/>
          <div style={{ display:"flex", flexDirection:"column", gap: 5 }}>
            {lines.map((l, i) => (
              <div key={i} style={{
                color: l.color, fontSize: 10, lineHeight: 1.6,
                fontFamily: T.mono, wordBreak:"break-all",
                display:"flex", alignItems:"center", gap: 5,
              }}>
                <span>{l.text}</span>
                {l.blink && <span style={{
                  width: 6, height: 12, background: T.accent, display:"inline-block",
                  animation:"blink 1s step-end infinite",
                }}/>}
              </div>
            ))}
          </div>
        </div>

        {/* progress bar */}
        <div style={{ marginBottom: 8 }}>
          <div style={{ display:"flex", justifyContent:"space-between", marginBottom: 5 }}>
            <span style={{ color: T.textDim, fontSize: 9 }}>receiving objects</span>
            <span style={{ color: T.accent, fontSize: 9, fontWeight: 600 }}>{pct}%</span>
          </div>
          <div style={{ height: 4, background: T.border2, borderRadius: 4, overflow:"hidden" }}>
            <div style={{
              height:"100%", width:`${pct}%`,
              background: `linear-gradient(90deg, ${T.accentMid}, ${T.accent})`,
              borderRadius: 4,
              boxShadow: `0 0 8px ${T.accentMid}`,
            }}/>
          </div>
        </div>
        <div style={{ color: T.textFaint, fontSize: 9, marginBottom: 20 }}>62 / 84 objects · 1.2 MB / 1.7 MB</div>

        {/* cancel */}
        <div style={{
          border: `1px solid ${T.border2}`, borderRadius: T.r,
          padding:"10px 16px", textAlign:"center",
          color: T.textDim, fontSize: 11, cursor:"pointer",
        }}>cancel</div>
      </div>
    </Phone>
  );
}
window.ScreenCloneLoading = ScreenCloneLoading;
