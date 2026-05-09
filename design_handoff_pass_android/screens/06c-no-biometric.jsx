function ScreenNoBiometric() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{ display:"flex", flexDirection:"column", height:"100%", position:"relative" }}>
        {/* dimmed entry-decrypting in background */}
        <div style={{ opacity:0.22, pointerEvents:"none", display:"flex", flexDirection:"column", flex:1 }}>
          <div style={{ padding:"12px 18px 10px", borderBottom:`1px solid ${T.border}`, display:"flex", alignItems:"center", gap:10 }}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M9 11L5 7L9 3" stroke={T.textDim} strokeWidth="1.4" strokeLinecap="round"/></svg>
            <div>
              <div style={{ color:T.textDim, fontSize:9 }}>email/gmail</div>
              <div style={{ color:T.accent, fontSize:14, fontWeight:700 }}>gmail</div>
            </div>
          </div>
          <div style={{ flex:1, padding:"16px 18px", display:"flex", flexDirection:"column", gap:0 }}>
            <div style={{ color:T.accent, fontSize:9, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:6, fontWeight:600 }}>password</div>
            <div style={{ background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, padding:"14px 14px", marginBottom:6 }}>
              <div style={{ display:"flex", alignItems:"center", gap:10, marginBottom:8 }}>
                <div style={{ width:14, height:14, borderRadius:"50%", border:`2px solid ${T.border2}`, borderTopColor:T.accent, flexShrink:0 }}/>
                <span style={{ color:T.textDim, fontSize:10 }}>decrypting…</span>
              </div>
              <div style={{ display:"flex", gap:6 }}>
                <Shimmer height={28} radius={T.r} style={{flex:1}}/>
                <Shimmer height={28} radius={T.r} style={{flex:1}}/>
              </div>
            </div>
            <div style={{ color:T.textFaint, fontSize:9, marginBottom:16 }}>decrypted in-memory · auto-clears in 45s</div>
            <div style={{ color:T.accent, fontSize:9, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:6, fontWeight:600 }}>notes</div>
            <div style={{ background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, padding:"10px 14px", display:"flex", flexDirection:"column", gap:7 }}>
              <Shimmer width="80%" height={9}/>
              <Shimmer width="55%" height={9}/>
            </div>
          </div>
        </div>

        {/* scrim */}
        <div style={{ position:"absolute", inset:0, background:"rgba(0,0,0,0.55)", pointerEvents:"none" }}/>

        {/* bottom sheet */}
        <div style={{
          position:"absolute", bottom:0, left:0, right:0,
          background:T.surface, borderTop:`1px solid ${T.border2}`,
          borderRadius:"16px 16px 0 0",
          padding:"10px 24px 32px",
        }}>
          {/* handle */}
          <div style={{ width:32, height:3, background:T.border2, borderRadius:4, margin:"0 auto 24px" }}/>

          {/* lock icon */}
          <div style={{
            width:56, height:56, borderRadius:14,
            background:T.surface, border:`1px solid ${T.border2}`,
            display:"flex", alignItems:"center", justifyContent:"center",
            margin:"0 auto 16px",
          }}>
            <svg width="26" height="26" viewBox="0 0 26 26" fill="none">
              <rect x="5" y="12" width="16" height="11" rx="2.5" stroke={T.textDim} strokeWidth="1.4"/>
              <path d="M9 12V9a4 4 0 0 1 8 0v3" stroke={T.textDim} strokeWidth="1.4" strokeLinecap="round"/>
              <circle cx="13" cy="17.5" r="1.5" fill={T.textDim}/>
              <line x1="13" y1="19" x2="13" y2="21" stroke={T.textDim} strokeWidth="1.4" strokeLinecap="round"/>
            </svg>
          </div>

          <div style={{ color:T.accent, fontSize:14, fontWeight:700, textAlign:"center", marginBottom:4 }}>unlock session</div>

          {/* no-biometric notice */}
          <div style={{
            display:"flex", alignItems:"center", justifyContent:"center", gap:5,
            marginBottom:20,
          }}>
            <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
              <circle cx="5.5" cy="5.5" r="4.5" stroke={T.textFaint} strokeWidth="1.1"/>
              <line x1="5.5" y1="3.5" x2="5.5" y2="6" stroke={T.textFaint} strokeWidth="1.1" strokeLinecap="round"/>
              <circle cx="5.5" cy="7.5" r="0.6" fill={T.textFaint}/>
            </svg>
            <span style={{ color:T.textFaint, fontSize:10 }}>no biometric auth enrolled on this device</span>
          </div>

          <Btn full>use passphrase</Btn>

          <div style={{
            marginTop:14, textAlign:"center",
            color:T.textFaint, fontSize:9, letterSpacing:"0.04em", lineHeight:1.8,
          }}>
            <span>enroll a fingerprint or face in </span>
            <span style={{ color:T.accentMid, textDecoration:"underline", cursor:"pointer" }}>Android Settings →</span>
          </div>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenNoBiometric = ScreenNoBiometric;
