function ScreenBiometric() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{ display:"flex", flexDirection:"column", height:"100%", position:"relative" }}>
        {/* dimmed home tree in background */}
        <div style={{ opacity:0.25, pointerEvents:"none", display:"flex", flexDirection:"column", flex:1 }}>
          <div style={{ padding:"12px 18px 10px", borderBottom:`1px solid ${T.border}`, display:"flex", alignItems:"center", justifyContent:"space-between" }}>
            <div>
              <div style={{ color:T.textDim, fontSize:9 }}>~/.password-store</div>
              <div style={{ color:T.accent, fontSize:14, fontWeight:700 }}>pass<span style={{ color:T.textDim, fontWeight:300 }}>.android</span></div>
            </div>
          </div>
          <div style={{ padding:"8px 18px", borderBottom:`1px solid ${T.border}` }}>
            <div style={{ background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, padding:"7px 12px", display:"flex", alignItems:"center", gap:8 }}>
              <span style={{ color:T.textDim, fontSize:11 }}>$</span>
              <span style={{ color:T.textDim, fontSize:11 }}>grep -r ""</span>
            </div>
          </div>
          <div style={{ padding:"6px 0" }}>
            {storeData.map(({folder, entries}) => (
              <div key={folder}>
                <div style={{ display:"flex", alignItems:"center", gap:8, padding:"7px 18px" }}>
                  <span style={{ color:T.accentMid, fontSize:10 }}>▼</span>
                  <span style={{ color:T.accent, fontSize:11, opacity:0.75 }}>{folder}/</span>
                </div>
                {entries.slice(0,2).map((e,i) => (
                  <div key={e} style={{ display:"flex", alignItems:"center", padding:"8px 18px 8px 40px", borderBottom:`1px solid ${T.border}` }}>
                    <span style={{ color:T.textFaint, fontSize:10, marginRight:8 }}>{i<1?"├─":"└─"}</span>
                    <span style={{ color:T.text, fontSize:11 }}>{e}</span>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>

        {/* scrim */}
        <div style={{ position:"absolute", inset:0, background:"rgba(0,0,0,0.5)", pointerEvents:"none" }}/>

        {/* bottom sheet */}
        <div style={{
          position:"absolute", bottom:0, left:0, right:0,
          background:T.surface, borderTop:`1px solid ${T.border2}`,
          borderRadius:"16px 16px 0 0",
          padding:"10px 24px 32px",
        }}>
          {/* handle */}
          <div style={{ width:32, height:3, background:T.border2, borderRadius:4, margin:"0 auto 24px" }}/>

          {/* fingerprint icon */}
          <div style={{
            width:56, height:56, borderRadius:14,
            background:T.accentDim, border:`1px solid ${T.accentMid}`,
            display:"flex", alignItems:"center", justifyContent:"center",
            margin:"0 auto 16px",
            boxShadow:`0 0 28px ${T.accentDim}`,
          }}>
            <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
              <path d="M9 7.5C10.5 6 12 5.5 14 5.5s4.5 1 6 3" stroke={T.accent} strokeWidth="1.4" strokeLinecap="round"/>
              <path d="M6 11c1-3 4-5.5 8-5.5s7 2.5 8 5.5" stroke={T.accent} strokeWidth="1.4" strokeLinecap="round" opacity="0.5"/>
              <path d="M11 10.5c.8-.8 1.8-1 3-1s2.2.2 3 1c.8.8 1 1.8 1 3v3" stroke={T.accent} strokeWidth="1.4" strokeLinecap="round"/>
              <path d="M11 13.5v-.5c0-1.7 1.3-3 3-3s3 1.3 3 3v3c0 1-.3 2-.8 2.8" stroke={T.accent} strokeWidth="1.4" strokeLinecap="round"/>
              <path d="M10 16c-.3-.7-.5-1.5-.5-2.5" stroke={T.accent} strokeWidth="1.4" strokeLinecap="round"/>
              <path d="M14 13.5v5c0 1.5-.5 3-1.5 4" stroke={T.accent} strokeWidth="1.4" strokeLinecap="round"/>
              <path d="M17 16.5c0 2-.8 4-2 5.5" stroke={T.accent} strokeWidth="1.4" strokeLinecap="round"/>
              <path d="M8 13c.2-3.3 2.7-6 6-6" stroke={T.accent} strokeWidth="1.4" strokeLinecap="round" opacity="0.3"/>
            </svg>
          </div>

          <div style={{ color:T.accent, fontSize:14, fontWeight:700, textAlign:"center", marginBottom:6 }}>unlock session</div>
          <div style={{ color:T.textDim, fontSize:10, textAlign:"center", marginBottom:24, lineHeight:1.6 }}>
            confirm your identity to resume
          </div>

          <Btn full>use fingerprint</Btn>
          <div style={{
            marginTop:10, padding:"10px 0", textAlign:"center",
            color:T.textDim, fontSize:10, cursor:"pointer",
          }}>use passphrase instead</div>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenBiometric = ScreenBiometric;
