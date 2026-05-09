// Mounts a screen component with theme state + Tweaks panel.
// Call once per screen HTML: mountScreen(<ScreenSplash/>).
function mountScreen(child) {
  function App() {
    const embedded = window.self !== window.top;
    const [theme, setTheme] = React.useState(() => {
      try { return localStorage.getItem('passAndroidTheme') || 'dark'; }
      catch (e) { return 'dark'; }
    });
    const [showTweaks, setShowTweaks] = React.useState(false);
    const T = theme === 'light' ? LIGHT_T : DARK_T;

    React.useEffect(() => {
      try { localStorage.setItem('passAndroidTheme', theme); } catch (e) {}
      document.body.style.background = theme === 'light' ? '#d9d4c8' : '#111';
    }, [theme]);

    React.useEffect(() => {
      const handler = (e) => {
        const d = e.data || {};
        if (d.type === '__activate_edit_mode')   setShowTweaks(true);
        if (d.type === '__deactivate_edit_mode') setShowTweaks(false);
        if (d.type === '__set_theme' && (d.theme === 'light' || d.theme === 'dark')) {
          setTheme(d.theme);
        }
      };
      window.addEventListener('message', handler);
      // Only top-level page advertises edit mode to host
      if (!embedded) {
        window.parent.postMessage({ type: '__edit_mode_available' }, '*');
      }
      return () => window.removeEventListener('message', handler);
    }, [embedded]);

    return (
      <ThemeContext.Provider value={T}>
        {child}
        {!embedded && showTweaks && (
          <div style={{
            position:'fixed', bottom:24, right:24, zIndex:9999,
            background:'#1a1a1a', border:'1px solid #333',
            borderRadius:12, padding:'16px 20px', minWidth:200,
            fontFamily:"'JetBrains Mono', monospace",
            boxShadow:'0 8px 32px rgba(0,0,0,0.5)',
          }}>
            <div style={{display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:14}}>
              <span style={{color:'#fff', fontSize:13, fontWeight:600}}>Tweaks</span>
              <span style={{color:'#666', cursor:'pointer', fontSize:16}} onClick={() => {
                setShowTweaks(false);
                window.parent.postMessage({type:'__edit_mode_dismissed'},'*');
              }}>×</span>
            </div>
            <div style={{display:'flex', alignItems:'center', justifyContent:'space-between'}}>
              <span style={{color:'#aaa', fontSize:11}}>Light mode</span>
              <div onClick={() => setTheme(t => t === 'dark' ? 'light' : 'dark')} style={{
                width:36, height:20, borderRadius:10, cursor:'pointer',
                background: theme === 'light' ? '#39ff6b' : '#333',
                position:'relative', transition:'background 0.2s',
              }}>
                <div style={{
                  width:14, height:14, borderRadius:'50%', background:'#fff',
                  position:'absolute', top:3,
                  left: theme === 'light' ? 19 : 3,
                  transition:'left 0.2s',
                }}/>
              </div>
            </div>
          </div>
        )}
      </ThemeContext.Provider>
    );
  }

  // Render after all babel scripts finish
  requestAnimationFrame(() => {
    ReactDOM.createRoot(document.getElementById('root')).render(<App/>);
  });
}

window.mountScreen = mountScreen;
