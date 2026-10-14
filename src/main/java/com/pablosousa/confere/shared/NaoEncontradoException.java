package com.pablosousa.confere.shared;
public class NaoEncontradoException extends RuntimeException {
    public NaoEncontradoException() { super("Recurso não encontrado."); }
}
