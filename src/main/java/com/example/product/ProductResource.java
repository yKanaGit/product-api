package com.example.product;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductResource {

    @GET
    public List<Product> list() {
        return Product.listAll();
    }

    @GET
    @Path("/{id}")
    public Product get(@PathParam("id") Long id) {
        Product product = Product.findById(id);
        if (product == null) {
            throw new WebApplicationException("Product with id " + id + " not found", 404);
        }
        return product;
    }

    @POST
    @Transactional
    public Response create(Product product) {
        product.persist();
        return Response.status(Response.Status.CREATED).entity(product).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Product update(@PathParam("id") Long id, Product updatedProduct) {
        Product product = Product.findById(id);
        if (product == null) {
            throw new WebApplicationException("Product with id " + id + " not found", 404);
        }
        product.name = updatedProduct.name;
        product.description = updatedProduct.description;
        product.price = updatedProduct.price;
        return product;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Product product = Product.findById(id);
        if (product == null) {
            throw new WebApplicationException("Product with id " + id + " not found", 404);
        }
        product.delete();
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
