function ScreenSettings() {
  const T = React.useContext(ThemeContext);
  const Toggle = ({on}) => (
    <div style={{
      width:32, height:18, borderRadius:9,
      background: on ? T.accent : T.border2,
      position:"relative", flexShrink:0,
    }}>
      <div style={{
        width:12, height:12, borderRadius:"50%", background:"#fff",
        position:"absolute", top:3,
        left: on ? 17 : 3,
      }}/>
    </div>
  );

  const Row = ({label, value, danger, static:isStatic, toggle, toggleOn}) => (
    <div style={{
      display:"flex", alignItems:"center",
      padding:"11px 14px",
      borderBottom:`1px solid ${T.border}`,
    }}>
      <span style={{color: danger ? T.danger : T.text, fontSize:11, flex:1}}>{label}</span>
      {value && <span style={{color:T.textDim, fontSize:10, marginRight:6}}>{value}</span>}
      {toggle && <Toggle on={toggleOn}/>}
      {!danger && !isStatic && !toggle && <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
        <path d="M3.5 8L6.5 5L3.5 2" stroke={T.textFaint} strokeWidth="1.2" strokeLinecap="round"/>
      </svg>}
    </div>
  );

  return (
    <Phone>
      <div style={{display:"flex", flexDirection:"column", height:"100%"}}>
        <div style={{
          padding:"12px 18px 10px",
          borderBottom:`1px solid ${T.border}`,
          flexShrink:0,
        }}>
          <div style={{color:T.accent, fontSize:14, fontWeight:700}}>settings</div>
        </div>

        <div style={{flex:1, overflowY:"auto", padding:"12px 18px"}}>
          {/* git */}
          <Label>git</Label>
          <div style={{background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, overflow:"hidden", marginBottom:14}}>
            <Row label="remote url" value="github.com:user/…" static/>
            <Row label="ssh key" value="ed25519" static/>
            <Row label="pull on open" toggle toggleOn={false}/>
          </div>

          {/* gpg */}
          <Label>gpg</Label>
          <div style={{background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, overflow:"hidden", marginBottom:14}}>
            <Row label="key id" value="A3F9 2B1C" static/>
            <Row label="uid" value="alice@example.com" static/>
          </div>

          {/* display */}
          <Label>display</Label>
          <div style={{background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, overflow:"hidden", marginBottom:14}}>
            <Row label="default view" value="tree"/>
            <Row label="clipboard timeout" value="45s"/>
            <Row label="session timeout" value="30 min"/>
            <Row label="biometric unlock" toggle toggleOn={false}/>
          </div>

          {/* danger zone */}
          <Label>store</Label>
          <div style={{background:T.surface, border:`1px solid ${T.border2}`, borderRadius:T.r, overflow:"hidden", marginBottom:14}}>
            <Row label="delete local store" danger/>
          </div>

          <div style={{textAlign:"center", color:T.textFaint, fontSize:9, marginTop:4}}>
            pass.android · v0.1.0 · linux pass compatible
          </div>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenSettings = ScreenSettings;
