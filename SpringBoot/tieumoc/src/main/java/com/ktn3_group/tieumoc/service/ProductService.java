package com.ktn3_group.tieumoc.service;

import com.ktn3_group.tieumoc.model.*;
import com.ktn3_group.tieumoc.model.response.ResultPaginationDTO;
import com.ktn3_group.tieumoc.model.response.product.ResProductDTO;
import com.ktn3_group.tieumoc.repository.*;
import com.ktn3_group.tieumoc.service.mapper.ProductMapper;
import com.ktn3_group.tieumoc.util.SecurityUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepo productRepo;
    private final ProductMapper productMapper;
    private final CategoryRepo categoryRepo;
    private final CompanyRepo companyRepo;
    private final FollowRepo followRepo;
    private final CompanyDetailRepo companyDetailRepo;

    public ProductService(ProductMapper productMapper,
                          ProductRepo productRepo,
                          CategoryRepo categoryRepo,
                          CompanyRepo companyRepo,
                          FollowRepo followRepo,
                          CompanyDetailRepo companyDetailRepo) {
        this.productRepo = productRepo;
        this.productMapper = productMapper;
        this.categoryRepo = categoryRepo;
        this.companyRepo = companyRepo;
        this.followRepo = followRepo;
        this.companyDetailRepo = companyDetailRepo;
    }

    // set company and category
//    public Product handleCreateProduct(Product request) {
//        // set quantity category
//        Category category = categoryRepo.findById(request.getCategory().getId()).orElse(null);
//        if (category != null) {
//            int quantity = category.getQuantity() + 1;
//            category.setQuantity(quantity);
//        }
//
//        // set quantity company
//        Company company = companyRepo.findById(request.getCompany().getId()).orElse(null);
//        if (company != null) {
//            int quantity = company.getQuantity() + 1;
//            company.setQuantity(quantity);
//        }
//
//        return productRepo.save(request);
//    }

    public Product handleCreateProduct(Product request) {
        Product product = productRepo.save(request);

        Category category = categoryRepo.findById(request.getCategory().getId()).orElse(null);
        if (category != null) {
            int quantity = productRepo.countByCategory(category);
            category.setQuantity(quantity);
        }

        Company company = companyRepo.findById(request.getCompany().getId()).orElse(null);
        if (company != null) {
            int quantity = productRepo.countByCompany(company);
            company.setQuantity(quantity);
        }

        return product;
    }

    public Product handleFetchProductById(long id) {
        Optional<Product> product = productRepo.findById(id);
        return product.orElse(null);
    }

    public boolean existsByProductName(String name) {
        return productRepo.existsByName(name);
    }

    public Product handleUpdateProduct(Product request) {
        Product p = productRepo.findById(request.getId()).orElse(null);
        Category current_category = new Category();
        Company current_company = new Company();
        if (p != null) {
            current_category = categoryRepo.findById(p.getCategory().getId()).orElse(null);
            current_company = companyRepo.findById(p.getCompany().getId()).orElse(null);
        }


        Product currentProduct = handleFetchProductById(request.getId());
        CompanyDetail companyDetail = new CompanyDetail();
        if (currentProduct != null) {
            if (currentProduct.getCompany() != null) {
                Company company = companyRepo.findById(currentProduct.getCompany().getId()).orElse(null);
                Product product = productRepo.findById(currentProduct.getId()).orElse(null);
                if (company != null && product != null) {
                    companyDetail = companyDetailRepo.findByCompanyAndProduct(
                            company, product
                    ).orElse(null);
                }
            }

            currentProduct.setName(request.getName());
            currentProduct.setCategory(request.getCategory());
            currentProduct.setCompany(request.getCompany());
            currentProduct.setDescription(request.getDescription());
            currentProduct.setActive(request.isActive());
            currentProduct.setExportPrice(request.getExportPrice());
            currentProduct.setNumberWarning(request.getNumberWarning());

            // update
            currentProduct = productRepo.save(currentProduct);

            if (companyDetail != null) {
                companyDetail.setCompany(currentProduct.getCompany());
                companyDetail.setProduct(currentProduct);

                companyDetailRepo.save(companyDetail);
            }

            Category category = categoryRepo.findById(request.getCategory().getId()).orElse(null);
            if (category != null) {
                int quantity = productRepo.countByCategory(category);
                category.setQuantity(quantity);
                categoryRepo.save(category);

                if (!current_category.equals(category)) {
                    current_category.setQuantity(current_category.getQuantity() - 1);
                    categoryRepo.save(current_category);
                }
            }

            Company company = companyRepo.findById(request.getCompany().getId()).orElse(null);
            if (company != null) {
                int quantity = productRepo.countByCompany(company);
                company.setQuantity(quantity);
                companyRepo.save(company);

                if (!current_company.equals(company)) {
                    current_company.setQuantity(current_company.getQuantity() - 1);
                    companyRepo.save(current_company);
                }
            }

        }

        return currentProduct;
    }

    public List<ResProductDTO> handleFetchAllProduct() {
        List<Product> products = productRepo.findAll();

        // remove sensitive data
        List<ResProductDTO> listProduct = products
                .stream().map(productMapper::convertToResProductDTO)
                .collect(Collectors.toList());

        return listProduct;
    }

    public void handleDeleteProduct(long id) {
        Product product = productRepo.findById(id).orElse(null);

        if (product != null) {
            Category category = categoryRepo.findById(product.getCategory().getId()).orElse(null);
            if (category != null) {
                int quantity = productRepo.countByCategory(category) - 1;
                category.setQuantity(quantity);
                categoryRepo.save(category);
            }

            Company company = companyRepo.findById(product.getCompany().getId()).orElse(null);
            if (company != null) {
                int quantity = productRepo.countByCompany(company) - 1;
                company.setQuantity(quantity);
                companyRepo.save(company);
            }
        }

        productRepo.deleteById(id);
    }

    public ResultPaginationDTO handleFetchAllProduct(
            Specification<Product> spec,
            Pageable pageable) {
        Page<Product> pageProduct = productRepo.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageProduct.getTotalPages());
        mt.setTotal(pageProduct.getTotalElements());

        rs.setMeta(mt);

        // remove sensitive data
        List<ResProductDTO> listProduct = pageProduct.getContent()
                .stream().map(productMapper::convertToResProductDTO)
                .collect(Collectors.toList());

        rs.setResult(listProduct);

        return rs;
    }

    // all product true active
    public ResultPaginationDTO handleFetchAllProductActive(
            Specification<Product> spec,
            Pageable pageable) {
        List<Product> products = productRepo.findAllByActiveTrue();


        Page<Product> pageProduct = new PageImpl<>(products, pageable, products.size());

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());
        mt.setPages((products.size() / pageable.getPageSize() == 0) ? products.size() / pageable.getPageSize() : products.size() / pageable.getPageSize() + 1);
        mt.setTotal(products.size());

        rs.setMeta(mt);

        List<ResProductDTO> resProductDTOS = pageProduct.getContent()
                .stream()
                .map(productMapper::convertToResProductDTO)
                .collect(Collectors.toList());

        rs.setResult(resProductDTOS);

        System.out.println("getPage" + mt.getPage());
        System.out.println("getPages" + mt.getPages());
        System.out.println("getPageSize" + mt.getPageSize());
        System.out.println("getTotal" + mt.getTotal());

        return rs;
    }


    // hàng sắp hết
    public List<ResProductDTO> handleFetchAllProductLowStock() {
        List<Product> products = productRepo.findAllByActiveTrue();

        List<Product> listProduct = products
                .stream()
                .filter(product -> product.getQuantity() <= product.getNumberWarning()) // Thêm điều kiện active = true
                .collect(Collectors.toList());

        // remove sensitive data and filter products with quantity <= numberWarning and active = true
        List<ResProductDTO> resProductDTOS = listProduct
                .stream()
                .map(productMapper::convertToResProductDTO)
                .collect(Collectors.toList());

        return resProductDTOS;
    }


    public ResultPaginationDTO handleFetchAllProductLowStock(
            Specification<Product> spec,
            Pageable pageable) {
        List<Product> products = productRepo.findAllByActiveTrue();

        List<Product> listProduct = products
                .stream()
                .filter(product -> product.getQuantity() <= product.getNumberWarning()) // Thêm điều kiện active = true
                .collect(Collectors.toList());

        Page<Product> pageProduct = new PageImpl<Product>(listProduct, pageable, listProduct.size());

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());
        mt.setPages((listProduct.size() / pageable.getPageSize() == 0) ? (listProduct.size() / pageable.getPageSize()) : (listProduct.size() / pageable.getPageSize() + 1));
        mt.setTotal(listProduct.size());

        rs.setMeta(mt);

        // remove sensitive data and filter products with quantity <= numberWarning and active = true
        List<ResProductDTO> resProductDTOS = pageProduct.getContent()
                .stream()
                .map(productMapper::convertToResProductDTO)
                .collect(Collectors.toList());

        rs.setResult(resProductDTOS);

        return rs;
    }


    // quan tâm
    public List<Product> findTop2ByOrderByFollowDesc() {
        String createdBy = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";
        List<Follow> follows = followRepo.findTop2ByCreatedByOrderByFollowDesc(createdBy);
        List<Product> products = new ArrayList<>();

        for (Follow follow : follows) {
            Product product = productRepo.findById(follow.getProduct().getId()).orElse(null);
            if (product != null) {
                products.add(product);
            }
        }

        return products;
    }

    public ResultPaginationDTO handleFetchAllProductByCategoryOfTopFollowed(
            Specification<Product> spec,
            Pageable pageable) {
        // Lấy danh sách sản phẩm từ Follow và lọc chỉ sản phẩm active = true
        List<Product> topFollowedProducts = findTop2ByOrderByFollowDesc().stream()
                .filter(product -> product.isActive()) // Lọc sản phẩm có active = true
                .collect(Collectors.toList());

        // Lấy danh sách idCategory từ các sản phẩm đã lọc
        List<Long> idCategories = topFollowedProducts.stream()
                .map(product -> product.getCategory().getId()) // Lấy ID từ Category
                .distinct()
                .collect(Collectors.toList());

        // Kết hợp Specification để lọc các sản phẩm có idCategory phù hợp
        Specification<Product> categorySpec = (root, query, criteriaBuilder) ->
                root.join("category").get("id").in(idCategories);

        // Kết hợp spec với categorySpec nếu có
        if (spec != null) {
            spec = spec.and(categorySpec);
        } else {
            spec = categorySpec;
        }

        // Gọi repository để lấy kết quả phân trang
        return handleFetchAllProduct(spec, pageable);
    }

    public List<Product> findAllByCompany(Company company) {
        return productRepo.findAllByCompany(company);
    }

    public List<Product> findAllByCategory(Category category) {
        return productRepo.findAllByCategory(category);
    }

}
