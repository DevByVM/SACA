import { useState } from "react";
import Login from "./pages/Login";
import Panel from "./pages/Panel";

function App() {

    const [logueado, setLogueado] =
        useState(false);

    if (logueado) {
        return <Panel />;
    }

    return (
        <Login
            onLoginSuccess={() =>
                setLogueado(true)
            }
        />
    );
}

export default App;