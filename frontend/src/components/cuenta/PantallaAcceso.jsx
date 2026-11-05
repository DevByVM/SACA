import Tarjeta from "../ui/Tarjeta";

// Marco común de las pantallas sin sesión: inicio de sesión, registro, reenvío y activación de la cuenta
function PantallaAcceso({ titulo, children }) {
    return (
        <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-8 sm:p-6">
            <Tarjeta className="w-full max-w-md p-6 shadow-md sm:p-8">
                <div className="mb-6 flex flex-col items-center text-center">
                    <div className="mb-4 flex h-20 w-20 items-center justify-center rounded-full border border-brand-200 bg-brand-50 text-brand-700">
                        <i className="fa-solid fa-graduation-cap text-4xl" aria-hidden="true"></i>
                    </div>

                    <h1 className="text-3xl font-bold text-ink-900">
                        SACA UES
                    </h1>

                    <p className="mt-1 text-sm text-ink-600">
                        Sistema de Análisis de Carga Académica
                    </p>

                    <div className="mt-4 h-1 w-16 rounded-full bg-brand-700" aria-hidden="true"></div>
                </div>

                <h2 className="mb-6 text-center text-lg font-semibold text-brand-700">
                    {titulo}
                </h2>

                {children}
            </Tarjeta>
        </main>
    );
}

export default PantallaAcceso;
